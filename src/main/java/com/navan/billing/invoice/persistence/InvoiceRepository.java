package com.navan.billing.invoice.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.navan.billing.invoice.domain.Invoice;
import com.navan.billing.invoice.domain.InvoiceDraft;
import com.navan.billing.transaction.service.InvoiceReferenceChecker;

/**
 * Persistence seam for invoices: header, snapshot lines, tax and fee rollups. SQL only; the service owns the
 * transaction boundary.
 */
@Repository
public class InvoiceRepository implements InvoiceReferenceChecker {

    private final JdbcClient jdbc;
    private final SnapshotCodec codec;

    public InvoiceRepository(JdbcClient jdbc, SnapshotCodec codec) {
        this.jdbc = jdbc;
        this.codec = codec;
    }

    /** TODO: insert header + lines + both rollups. Returns new invoice rowid. */
    public long insert(InvoiceDraft draft, Instant now) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: replace lines + rollups + header totals of an existing invoice. Returns affected header rows (0 = gone). */
    public int replace(long id, InvoiceDraft draft, Instant now) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: full invoice from snapshot tables ONLY. Must not read the live transactions table. */
    public Optional<Invoice> findById(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: keyset page (id &gt; afterId ORDER BY id LIMIT n). Decide whether list items include lines or just headers. */
    public List<Invoice> findPage(long afterId, int limitPlusOne) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: delete invoice; lines/rollups cascade. Transactions untouched. Returns row count. */
    public int deleteById(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public boolean isInvoiced(long transactionId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM invoice_lines WHERE transaction_id = :id)")
                .param("id", transactionId)
                .query(Boolean.class)
                .single();
    }
}
