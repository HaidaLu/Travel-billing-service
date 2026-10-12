package com.navan.billing.invoice.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.navan.billing.common.id.PublicId;
import com.navan.billing.common.money.MinorUnits;
import com.navan.billing.common.time.DbTime;
import com.navan.billing.invoice.dto.FeeRollupLine;
import com.navan.billing.invoice.dto.Invoice;
import com.navan.billing.invoice.dto.InvoiceDraft;
import com.navan.billing.invoice.dto.TaxRollupLine;
import com.navan.billing.invoice.dto.TransactionSnapshot;
import com.navan.billing.transaction.service.InvoiceReferenceChecker;

/**
 * Persistence seam for invoices: header, snapshot lines, tax and fee rollups. SQL only; the service owns the
 * transaction boundary ({@code MANDATORY}, as in TransactionRepository: several methods write more than one table).
 *
 * <p>Reads touch the four invoice tables only, never {@code transactions}: that is what keeps an invoice frozen.
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class InvoiceRepository implements InvoiceReferenceChecker {

    private static final RowMapper<Header> HEADER = (rs, n) -> new Header(
            rs.getLong("id"),
            rs.getString("currency"),
            MinorUnits.fromMinor(rs.getLong("total_minor")),
            DbTime.parse(rs.getString("created_at")),
            DbTime.parse(rs.getString("updated_at")));

    private static final RowMapper<TaxRollupLine> TAX_ROLLUP = (rs, n) -> {
        String rate = rs.getString("rate");
        return new TaxRollupLine(rs.getString("name"), rate == null ? null : new BigDecimal(rate),
                MinorUnits.fromMinor(rs.getLong("amount_minor")));
    };

    private static final RowMapper<FeeRollupLine> FEE_ROLLUP = (rs, n) ->
            new FeeRollupLine(rs.getString("name"), MinorUnits.fromMinor(rs.getLong("amount_minor")));

    private final JdbcClient jdbc;
    private final SnapshotCodec codec;

    public InvoiceRepository(JdbcClient jdbc, SnapshotCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    /** Header + lines + both rollups. Returns the new invoice rowid. */
    public long insert(InvoiceDraft draft, Instant now) {
        long id = jdbc.sql("""
                        INSERT INTO invoices (currency, total_minor, created_at, updated_at)
                        VALUES (:currency, :total, :now, :now)
                        RETURNING id""")
                .param("currency", draft.currency())
                .param("total", MinorUnits.toMinor(draft.total()))
                .param("now", DbTime.format(now))
                .query(Long.class)
                .single();
        insertChildren(id, draft);
        return id;
    }

    /** Swaps lines + rollups wholesale and rewrites the header totals. Returns affected header rows (0 = gone). */
    public int replace(long id, InvoiceDraft draft, Instant now) {
        int updated = jdbc.sql("""
                        UPDATE invoices SET currency = :currency, total_minor = :total, updated_at = :now
                        WHERE id = :id""")
                .param("id", id)
                .param("currency", draft.currency())
                .param("total", MinorUnits.toMinor(draft.total()))
                .param("now", DbTime.format(now))
                .update();
        if (updated == 0) {
            return 0;
        }
        for (String table : List.of("invoice_lines", "invoice_tax_rollup", "invoice_fee_rollup")) {
            jdbc.sql("DELETE FROM " + table + " WHERE invoice_id = :id").param("id", id).update();
        }
        insertChildren(id, draft);
        return updated;
    }

    /** Full invoice from the snapshot tables only. */
    public Optional<Invoice> findById(long id) {
        return jdbc.sql("SELECT id, currency, total_minor, created_at, updated_at FROM invoices WHERE id = :id")
                .param("id", id)
                .query(HEADER)
                .optional()
                .map(this::hydrate);
    }

    /** Keyset page, same shape as the transaction list: fully hydrated, at most limit + 1 rows. */
    public List<Invoice> findPage(long afterId, int limitPlusOne) {
        return jdbc.sql("""
                        SELECT id, currency, total_minor, created_at, updated_at
                        FROM invoices WHERE id > :afterId ORDER BY id LIMIT :limit""")
                .param("afterId", afterId)
                .param("limit", limitPlusOne)
                .query(HEADER)
                .list()
                .stream()
                .map(this::hydrate)
                .toList();
    }

    /** Lines and rollups go via ON DELETE CASCADE. The transactions they reference are untouched. */
    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM invoices WHERE id = :id").param("id", id).update();
    }

    /** The invoice a transaction is on, if any (served by ux_invoice_lines_transaction_id). */
    public OptionalLong findInvoiceContaining(long transactionId) {
        return jdbc.sql("SELECT invoice_id FROM invoice_lines WHERE transaction_id = :id LIMIT 1")
                .param("id", transactionId)
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    /** Served by ux_invoice_lines_transaction_id; EXISTS stops at the first matching line. */
    @Override
    public boolean isInvoiced(long transactionId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM invoice_lines WHERE transaction_id = :id)")
                .param("id", transactionId)
                .query(Boolean.class)
                .single();
    }

    // ─── internals ──────────────────────────────────────────────────────────────

    private void insertChildren(long invoiceId, InvoiceDraft draft) {
        List<TransactionSnapshot> lines = draft.lines();
        for (int position = 0; position < lines.size(); position++) {
            TransactionSnapshot line = lines.get(position);
            jdbc.sql("""
                            INSERT INTO invoice_lines (invoice_id, position, transaction_id, type, external_id,
                                                       total_minor, snapshot_json)
                            VALUES (:invoiceId, :position, :transactionId, :type, :externalId, :total, :json)""")
                    .param("invoiceId", invoiceId)
                    .param("position", position)
                    .param("transactionId", PublicId.TRANSACTION.decode(line.transactionId()))
                    .param("type", line.type().wire())
                    .param("externalId", line.externalId())
                    .param("total", MinorUnits.toMinor(line.total()))
                    .param("json", codec.encode(line))
                    .update();
        }
        List<TaxRollupLine> taxes = draft.taxRollup();
        for (int position = 0; position < taxes.size(); position++) {
            TaxRollupLine tax = taxes.get(position);
            jdbc.sql("""
                            INSERT INTO invoice_tax_rollup (invoice_id, position, name, rate, amount_minor)
                            VALUES (:invoiceId, :position, :name, :rate, :amount)""")
                    .param("invoiceId", invoiceId)
                    .param("position", position)
                    .param("name", tax.name())
                    .param("rate", tax.rate() == null ? null : tax.rate().toPlainString())
                    .param("amount", MinorUnits.toMinor(tax.amount()))
                    .update();
        }
        List<FeeRollupLine> fees = draft.feeRollup();
        for (int position = 0; position < fees.size(); position++) {
            FeeRollupLine fee = fees.get(position);
            jdbc.sql("""
                            INSERT INTO invoice_fee_rollup (invoice_id, position, name, amount_minor)
                            VALUES (:invoiceId, :position, :name, :amount)""")
                    .param("invoiceId", invoiceId)
                    .param("position", position)
                    .param("name", fee.name())
                    .param("amount", MinorUnits.toMinor(fee.amount()))
                    .update();
        }
    }
    // seperate from transaction
    private Invoice hydrate(Header h) {
        List<TransactionSnapshot> lines = jdbc.sql("""
                        SELECT snapshot_json FROM invoice_lines WHERE invoice_id = :id ORDER BY position""")
                .param("id", h.id())
                .query(String.class)
                .list()
                .stream()
                .map(codec::decode)
                .toList();
        List<TaxRollupLine> taxes = jdbc.sql("""
                        SELECT name, rate, amount_minor FROM invoice_tax_rollup
                        WHERE invoice_id = :id ORDER BY position""")
                .param("id", h.id())
                .query(TAX_ROLLUP)
                .list();
        List<FeeRollupLine> fees = jdbc.sql("""
                        SELECT name, amount_minor FROM invoice_fee_rollup
                        WHERE invoice_id = :id ORDER BY position""")
                .param("id", h.id())
                .query(FEE_ROLLUP)
                .list();
        return new Invoice(h.id(), h.currency(), h.total(), lines, taxes, fees, h.createdAt(), h.updatedAt());
    }

    /** Header row before hydration. */
    private record Header(long id, String currency, BigDecimal total, Instant createdAt, Instant updatedAt) {
    }
}
