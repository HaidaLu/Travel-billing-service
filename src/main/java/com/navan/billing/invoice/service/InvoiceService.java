package com.navan.billing.invoice.service;

import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.navan.billing.common.error.ConflictException;
import com.navan.billing.common.error.NotFoundException;
import com.navan.billing.common.id.PublicId;
import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.invoice.domain.Invoice;
import com.navan.billing.invoice.domain.InvoiceDraft;
import com.navan.billing.invoice.persistence.InvoiceRepository;
import com.navan.billing.invoice.snapshot.InvoiceSnapshotBuilder;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.persistence.TransactionRepository;

/**
 * Use-case orchestration and the @Transactional boundary for invoices.
 *
 * <p>Each write is one DB transaction: load the transactions, build the snapshot, write it. With
 * transaction_mode=IMMEDIATE the write lock is held from BEGIN, so a concurrent PATCH can't change a transaction
 * between "read" and "snapshot written", and two requests can't both put the same transaction on an invoice.
 */
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

    /** 409 on a repeated id, a transaction already on an invoice, or mixed currency; 404 on a missing id. */
    @Transactional
    public Invoice create(List<Long> transactionIds) {
        InvoiceDraft draft = draft(transactionIds, OptionalLong.empty());
        long id = invoices.insert(draft, clock.instant());
        return findOrThrow(id);
    }

    /** Served entirely from the snapshot tables. */
    @Transactional(readOnly = true)
    public Invoice get(long id) {
        return findOrThrow(id);
    }

    @Transactional(readOnly = true)
    public Page<Invoice> list(PageRequest page) {
        List<Invoice> rows = invoices.findPage(page.afterId(), page.limit() + 1);
        return Page.fromLookahead(rows, page.limit(), Invoice::id);
    }

    /**
     * Replaces the transaction set and rebuilds the snapshot from the CURRENT stored transactions. Same rules as
     * create, except a transaction already on THIS invoice is fine. Any failure leaves the invoice unchanged:
     * every check runs before the first write, and the transaction rolls back regardless.
     */
    @Transactional
    public Invoice replace(long id, List<Long> transactionIds) {
        findOrThrow(id);
        InvoiceDraft draft = draft(transactionIds, OptionalLong.of(id));
        invoices.replace(id, draft, clock.instant());
        return findOrThrow(id);
    }

    /** The invoice and its snapshot rows go; the transactions stay (and become editable again). */
    @Transactional
    public void delete(long id) {
        if (invoices.deleteById(id) == 0) {
            throw notFound(id);
        }
    }

    /** All request-level rules, in order: repeated id → 409, missing id → 404, invoiced elsewhere → 409. */
    private InvoiceDraft draft(List<Long> transactionIds, OptionalLong invoiceBeingReplaced) {
        Set<Long> seen = new HashSet<>();
        for (long txId : transactionIds) {
            if (!seen.add(txId)) {
                throw ConflictException.duplicateInvoiceLine(PublicId.TRANSACTION.encode(txId));
            }
        }
        List<Transaction> loaded = transactionIds.stream()
                .map(txId -> transactions.findById(txId).orElseThrow(() -> new NotFoundException(
                        "transaction " + PublicId.TRANSACTION.encode(txId) + " not found")))
                .toList();
        for (long txId : transactionIds) {
            OptionalLong current = invoices.findInvoiceContaining(txId);
            if (current.isPresent() && !current.equals(invoiceBeingReplaced)) {
                throw ConflictException.alreadyInvoiced(PublicId.TRANSACTION.encode(txId),
                        PublicId.INVOICE.encode(current.getAsLong()));
            }
        }
        return builder.build(loaded);
    }

    private Invoice findOrThrow(long id) {
        return invoices.findById(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("invoice " + PublicId.INVOICE.encode(id) + " not found");
    }
}
