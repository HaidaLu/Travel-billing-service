package com.navan.billing.transaction.service;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.navan.billing.common.error.BadRequestException;
import com.navan.billing.common.error.ConflictException;
import com.navan.billing.common.error.NotFoundException;
import com.navan.billing.common.id.PublicId;
import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.transaction.api.PatchTransactionRequest;
import com.navan.billing.transaction.domain.NewTransaction;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.TransactionMetadata;
import com.navan.billing.transaction.identity.IdentityClaim;
import com.navan.billing.transaction.identity.TransactionIdentityService;
import com.navan.billing.transaction.persistence.TransactionRepository;
import com.navan.billing.transaction.persistence.metadata.MetadataStore;
import com.navan.billing.transaction.persistence.metadata.MetadataStoreRegistry;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

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
    private final Validator validator;
    private final Clock clock;

    public TransactionService(TransactionIdentityService identity, TransactionRepository repository,
                              MetadataStoreRegistry metadataStores, InvoiceReferenceChecker invoiceReferences,
                              ObjectMapper objectMapper, Validator validator, Clock clock) {
        this.identity = identity;
        this.repository = repository;
        this.metadataStores = metadataStores;
        this.invoiceReferences = invoiceReferences;
        this.objectMapper = objectMapper;
        this.validator = validator;
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
        return findOrThrow(id);
    }

    /** One bounded page plus a next cursor (null on the last page). The bound is in SQL; nothing pages in memory. */
    @Transactional(readOnly = true)
    public Page<Transaction> list(PageRequest page) {
        List<Transaction> rows = repository.findPage(page.afterId(), page.limit() + 1);
        return Page.fromLookahead(rows, page.limit(), Transaction::id);
    }

    /**
     * Partial update: an absent field stays unchanged, a list that is sent replaces the stored list, and metadata
     * (if sent) replaces the stored metadata and must be the full shape of the STORED type.
     *
     * <p>404 if missing; 409 if the transaction is on any invoice, and nothing is written. The invoiced check and the
     * writes share one DB transaction; with transaction_mode=IMMEDIATE it holds SQLite's write lock from BEGIN, so a
     * concurrent POST /invoices can't snapshot this row between the check and the write. (On Postgres this needs a
     * row lock, e.g. SELECT ... FOR UPDATE on the transaction, taken by both sides.)
     */
    @Transactional
    public Transaction patch(long id, PatchTransactionRequest patch) {
        Transaction current = findOrThrow(id);
        if (invoiceReferences.isInvoiced(id)) {
            throw ConflictException.transactionInvoiced(PublicId.TRANSACTION.encode(id));
        }
        TransactionMetadata metadata = isAbsent(patch.metadata()) ? null : toMetadata(current.type(), patch.metadata());

        repository.updateHeader(id,
                patch.occurredAt() != null ? patch.occurredAt() : current.occurredAt(),
                patch.total() != null ? patch.total() : current.total(),
                clock.instant());
        if (patch.taxLines() != null) {
            repository.replaceTaxLines(id, patch.taxLines());
        }
        if (patch.feeLines() != null) {
            repository.replaceFeeLines(id, patch.feeLines());
        }
        if (metadata != null) {
            MetadataStore<TransactionMetadata> store = metadataStores.forType(current.type());
            store.delete(id);
            store.insert(id, metadata);
        }
        return findOrThrow(id);
    }

    /** 409 and delete nothing if invoiced; otherwise the header goes and its children cascade, in one transaction. */
    @Transactional
    public void delete(long id) {
        if (invoiceReferences.isInvoiced(id)) {
            throw ConflictException.transactionInvoiced(PublicId.TRANSACTION.encode(id));
        }
        if (repository.deleteById(id) == 0) {
            throw notFound(id);
        }
    }

    private Transaction findOrThrow(long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("transaction " + PublicId.TRANSACTION.encode(id) + " not found");
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull();
    }

    /** PATCH metadata carries no type, so it is bound with the stored type's class, then validated like on create. */
    private TransactionMetadata toMetadata(TransactionType type, JsonNode json) {
        TransactionMetadata metadata;
        try {
            metadata = objectMapper.treeToValue(json, metadataStores.forType(type).metadataClass());
        } catch (JsonProcessingException e) {
            throw new BadRequestException("invalid " + type.wire() + " metadata: " + e.getOriginalMessage());
        }
        Set<ConstraintViolation<TransactionMetadata>> violations = validator.validate(metadata);
        if (!violations.isEmpty()) {
            throw new BadRequestException(violations.stream()
                    .map(v -> "metadata." + v.getPropertyPath() + " " + v.getMessage())
                    .sorted()
                    .collect(Collectors.joining("; ")));
        }
        return metadata;
    }
}
