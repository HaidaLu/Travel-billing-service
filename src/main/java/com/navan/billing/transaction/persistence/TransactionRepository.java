package com.navan.billing.transaction.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.navan.billing.common.money.MinorUnits;
import com.navan.billing.common.time.DbTime;
import com.navan.billing.transaction.dto.FeeLine;
import com.navan.billing.transaction.dto.NewTransaction;
import com.navan.billing.transaction.dto.TaxLine;
import com.navan.billing.transaction.dto.Transaction;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.persistence.metadata.MetadataStoreRegistry;

/**
 * transactionRepository must be used in the transaction of service, otherwise will throw exception
 */
@Repository
@Transactional(propagation = Propagation.MANDATORY)
public class TransactionRepository {

    private static final RowMapper<Header> HEADER = (rs, n) -> new Header(
            rs.getLong("id"),
            TransactionType.fromWire(rs.getString("type")),
            rs.getString("external_id"),
            rs.getString("currency"),
            DbTime.parse(rs.getString("occurred_at")),
            MinorUnits.fromMinor(rs.getLong("total_minor")),
            DbTime.parse(rs.getString("created_at")),
            DbTime.parse(rs.getString("updated_at")));

    private static final RowMapper<TaxLine> TAX_LINE = (rs, n) -> {
        String rate = rs.getString("rate");
        return new TaxLine(rs.getString("name"), rate == null ? null : new BigDecimal(rate),
                MinorUnits.fromMinor(rs.getLong("amount_minor")));
    };

    private static final RowMapper<FeeLine> FEE_LINE = (rs, n) ->
            new FeeLine(rs.getString("name"), MinorUnits.fromMinor(rs.getLong("amount_minor")));

    private final JdbcClient jdbc;
    private final MetadataStoreRegistry metadataStores;

    public TransactionRepository(JdbcClient jdbc, MetadataStoreRegistry metadataStores) {
        this.jdbc = jdbc;
        this.metadataStores = metadataStores;
    }

    public OptionalLong insertHeaderIfAbsent(NewTransaction tx, Instant now) {
        return jdbc.sql("""
                        INSERT INTO transactions
                            (external_id, type, currency, occurred_at, total_minor, created_at, updated_at)
                        VALUES (:externalId, :type, :currency, :occurredAt, :total, :now, :now)
                        ON CONFLICT (external_id) DO NOTHING
                        RETURNING id""")
                .param("externalId", tx.externalId())
                .param("type", tx.type().wire())
                .param("currency", tx.currency())
                .param("occurredAt", DbTime.format(tx.occurredAt()))
                .param("total", MinorUnits.toMinor(tx.total()))
                .param("now", DbTime.format(now))
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    /** Id of the row owning this external_id (served by the unique index). */
    public OptionalLong findIdByExternalId(String externalId) {
        return jdbc.sql("SELECT id FROM transactions WHERE external_id = :externalId")
                .param("externalId", externalId)
                .query(Long.class)
                .optional()
                .map(OptionalLong::of)
                .orElseGet(OptionalLong::empty);
    }

    /** Tax lines, fee lines and type-specific metadata for a header that was just inserted. */
    public void insertChildren(long id, NewTransaction tx) {
        insertTaxLines(id, tx.taxLines());
        insertFeeLines(id, tx.feeLines());
        metadataStores.insert(id, tx.metadata());
    }

    /** Fully hydrated transaction, list order preserved via {@code position}. Empty if missing. */
    public Optional<Transaction> findById(long id) {
        return jdbc.sql("""
                        SELECT id, type, external_id, currency, occurred_at, total_minor, created_at, updated_at
                        FROM transactions WHERE id = :id""")
                .param("id", id)
                .query(HEADER)
                .optional()
                .map(this::hydrate);
    }

    /**
     * Keyset page: rows with id &gt; afterId in id order, bounded by SQL LIMIT (a primary-key range seek, no OFFSET
     * scan). Callers pass limit + 1 to learn whether a next page exists. Each row is fully hydrated, which costs a
     * few queries per row (N+1); accepted because a page is at most 100 rows.
     */
    public List<Transaction> findPage(long afterId, int limitPlusOne) {
        return jdbc.sql("""
                        SELECT id, type, external_id, currency, occurred_at, total_minor, created_at, updated_at
                        FROM transactions WHERE id > :afterId ORDER BY id LIMIT :limit""")
                .param("afterId", afterId)
                .param("limit", limitPlusOne)
                .query(HEADER)
                .list()
                .stream()
                .map(this::hydrate)
                .toList();
    }

    /**
     * Overwrites the mutable header columns. Callers pass final values (the service merges a PATCH onto the
     * loaded row), so there are no "null means unchanged" semantics hidden in SQL. Returns affected rows.
     */
    public int updateHeader(long id, Instant occurredAt, BigDecimal total, Instant now) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(total, "total");
        return jdbc.sql("""
                        UPDATE transactions
                        SET occurred_at = :occurredAt, total_minor = :total, updated_at = :now
                        WHERE id = :id""")
                .param("id", id)
                .param("occurredAt", DbTime.format(occurredAt))
                .param("total", MinorUnits.toMinor(total))
                .param("now", DbTime.format(now))
                .update();
    }

    /** Replaces the whole tax-line list. Never a partial merge, so a concurrent reader can't see a mix. */
    public void replaceTaxLines(long id, List<TaxLine> lines) {
        jdbc.sql("DELETE FROM transaction_tax_lines WHERE transaction_id = :id").param("id", id).update();
        insertTaxLines(id, lines);
    }

    /** Replaces the whole fee-line list. */
    public void replaceFeeLines(long id, List<FeeLine> lines) {
        jdbc.sql("DELETE FROM transaction_fee_lines WHERE transaction_id = :id").param("id", id).update();
        insertFeeLines(id, lines);
    }

    /**
     * Deletes the header; lines and metadata go via ON DELETE CASCADE (needs foreign_keys=true on the connection).
     * If the row is referenced by invoice_lines, the FK makes this fail: a DB-level backstop for the service's 409.
     */
    public int deleteById(long id) {
        return jdbc.sql("DELETE FROM transactions WHERE id = :id").param("id", id).update();
    }

    // ─── internals ──────────────────────────────────────────────────────────────

    private Transaction hydrate(Header h) {
        return new Transaction(h.id(), h.type(), h.externalId(), h.currency(), h.occurredAt(), h.total(),
                loadTaxLines(h.id()), loadFeeLines(h.id()), metadataStores.load(h.type(), h.id()),
                h.createdAt(), h.updatedAt());
    }

    private void insertTaxLines(long id, List<TaxLine> lines) {
        for (int position = 0; position < lines.size(); position++) {
            TaxLine line = lines.get(position);
            jdbc.sql("""
                            INSERT INTO transaction_tax_lines (transaction_id, position, name, rate, amount_minor)
                            VALUES (:id, :position, :name, :rate, :amount)""")
                    .param("id", id)
                    .param("position", position)
                    .param("name", line.name())
                    .param("rate", line.rate() == null ? null : line.rate().toPlainString())
                    .param("amount", MinorUnits.toMinor(line.amount()))
                    .update();
        }
    }

    private void insertFeeLines(long id, List<FeeLine> lines) {
        for (int position = 0; position < lines.size(); position++) {
            FeeLine line = lines.get(position);
            jdbc.sql("""
                            INSERT INTO transaction_fee_lines (transaction_id, position, name, amount_minor)
                            VALUES (:id, :position, :name, :amount)""")
                    .param("id", id)
                    .param("position", position)
                    .param("name", line.name())
                    .param("amount", MinorUnits.toMinor(line.amount()))
                    .update();
        }
    }

    private List<TaxLine> loadTaxLines(long id) {
        return jdbc.sql("""
                        SELECT name, rate, amount_minor FROM transaction_tax_lines
                        WHERE transaction_id = :id ORDER BY position""")
                .param("id", id)
                .query(TAX_LINE)
                .list();
    }

    private List<FeeLine> loadFeeLines(long id) {
        return jdbc.sql("""
                        SELECT name, amount_minor FROM transaction_fee_lines
                        WHERE transaction_id = :id ORDER BY position""")
                .param("id", id)
                .query(FEE_LINE)
                .list();
    }

    /** Header row before hydration. */
    private record Header(long id, TransactionType type, String externalId, String currency, Instant occurredAt,
                          BigDecimal total, Instant createdAt, Instant updatedAt) {
    }
}
