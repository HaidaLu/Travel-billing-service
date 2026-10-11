package com.navan.billing.invoice.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;

import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.invoice.domain.Invoice;
import com.navan.billing.invoice.persistence.InvoiceRepository;
import com.navan.billing.invoice.snapshot.InvoiceSnapshotBuilder;
import com.navan.billing.transaction.persistence.TransactionRepository;

/** Use-case orchestration and the @Transactional boundary for invoices. */
@Service
public class InvoiceService {

    private final TransactionRepository transactions;
    private final InvoiceRepository invoices;
    private final InvoiceSnapshotBuilder builder;
    private final Clock clock;

    public InvoiceService(TransactionRepository transactions, InvoiceRepository invoices,
                          InvoiceSnapshotBuilder builder, Clock clock) {
        this.transactions = transactions;
        this.invoices = invoices;
        this.builder = builder;
        this.clock = clock;
    }

    /**
     * TODO: create an invoice from stored transactions, in one DB transaction.
     *  - Same id listed twice → decide: 409 (ConflictException.duplicateInvoiceLine) or dedupe. Never double-count.
     *  - Any id missing → 404, nothing written.
     *  - Load → build draft → insert all inside one transaction, so a concurrent PATCH can't change a
     *    transaction between "read" and "snapshot written".
     *  - Mixed currency → 409, nothing written.
     */
    public Invoice create(List<Long> transactionIds) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: 404 if missing. Served entirely from snapshot tables. */
    public Invoice get(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: bounded page plus a next cursor. */
    public Page<Invoice> list(PageRequest page) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * TODO: replace the transaction set and rebuild the snapshot from CURRENT stored transactions, atomically.
     *  - 404 if the invoice or any id is missing; 409 on mixed currency. Either way the invoice is unchanged.
     *  - Question: a transaction already on ANOTHER invoice — allowed (billed twice?) or 409? Also decide for create.
     */
    public Invoice replace(long id, List<Long> transactionIds) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: 404 if missing; delete invoice + snapshot rows; transactions stay. */
    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
