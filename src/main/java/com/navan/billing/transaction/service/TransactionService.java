package com.navan.billing.transaction.service;

import java.time.Clock;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.common.error.ConflictException;
import com.navan.billing.common.error.NotFoundException;
import com.navan.billing.common.id.PublicId;
import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.transaction.api.PatchTransactionRequest;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.identity.IdentityClaim;
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

    @Transactional
    public Transaction create(NewTransaction tx) {
        IdentityClaim claim = identity.claim(tx, clock.instant());
        if (claim instanceof IdentityClaim.AlreadyExists existing) {
            throw ConflictException.duplicateExternalId(tx.externalId(),
                    PublicId.TRANSACTION.encode(existing.existingId()));
        }
        long id = ((IdentityClaim.Claimed) claim).id();
        repository.insertChildren(id, tx);
        return repository.findById(id).orElseThrow();
    }

    @Transactional(readOnly = true)
    public Transaction get(long id) {
        return repository.findById(id).orElseThrow(() ->
                new NotFoundException("transaction " + PublicId.TRANSACTION.encode(id) + " not found"));
    }

    /** One bounded page plus a next cursor (null on the last page). The bound is in SQL; nothing pages in memory. */
    @Transactional(readOnly = true)
    public Page<Transaction> list(PageRequest page) {
        List<Transaction> rows = repository.findPage(page.afterId(), page.limit() + 1);
        return Page.fromLookahead(rows, page.limit(), Transaction::id);
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
