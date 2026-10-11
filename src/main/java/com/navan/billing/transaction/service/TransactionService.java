package com.navan.billing.transaction.service;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.transaction.api.PatchTransactionRequest;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.identity.TransactionIdentityService;
import com.navan.billing.transaction.persistence.TransactionRepository;
import com.navan.billing.transaction.persistence.metadata.MetadataStoreRegistry;

/**
 * Use-case orchestration and the @Transactional boundary for transactions.
 * Rules live here; SQL lives in the repository; identity lives in {@link TransactionIdentityService}.
 */
@Service
public class TransactionService {

    private final TransactionIdentityService identity;
    private final TransactionRepository repository;
    private final MetadataStoreRegistry metadataStores;
    private final InvoiceReferenceChecker invoiceReferences;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public TransactionService(TransactionIdentityService identity, TransactionRepository repository,
                              MetadataStoreRegistry metadataStores, InvoiceReferenceChecker invoiceReferences,
                              ObjectMapper objectMapper, Clock clock) {
        this.identity = identity;
        this.repository = repository;
        this.metadataStores = metadataStores;
        this.invoiceReferences = invoiceReferences;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * TODO: create a transaction atomically.
     *  - Header, tax lines, fee lines and metadata commit together or not at all.
     *  - Duplicate external_id → ConflictException.duplicateExternalId(...) carrying the existing public id;
     *    nothing new is persisted.
     *  - Business validation beyond bean validation (e.g. amount scale ≤ 2 → 400). Do totals need to reconcile
     *    with fare + taxes + fees? The fixtures don't always add up, so decide and document.
     *  - Return the stored record as GET would return it.
     */
    public Transaction create(NewTransaction tx) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: hydrated transaction, or NotFoundException. */
    public Transaction get(long id) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: one bounded page plus a next cursor (null on the last page). Never load the table to page in memory. */
    public Page<Transaction> list(PageRequest page) {
        throw new UnsupportedOperationException("TODO");
    }

    /**
     * TODO: partial update.
     *  - 404 if missing. 409 (ConflictException.transactionInvoiced) if on ANY invoice, and persist nothing.
     *  - The invoiced check and the write must be in the SAME DB transaction. Otherwise a concurrent
     *    POST /invoices can slip between them. What guarantees that here (see transaction_mode in application.yml)?
     *    What would you need on Postgres?
     *  - Lists are replaced wholesale. metadata JSON is converted using the STORED type's metadata class
     *    (wrong shape → 400) and validated like on create.
     *  - Bump updated_at.
     */
    public Transaction patch(long id, PatchTransactionRequest patch) {
        throw new UnsupportedOperationException("TODO");
    }

    /** TODO: 404 if missing; 409 and delete nothing if invoiced; otherwise delete header + children atomically. */
    public void delete(long id) {
        throw new UnsupportedOperationException("TODO");
    }
}
