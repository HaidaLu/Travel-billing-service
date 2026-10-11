package com.navan.billing.transaction.persistence.metadata;

import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.TransactionMetadata;

/**
 * Strategy per transaction type: knows that type's child tables and nothing else.
 * Adding a type = new metadata record + new store + migration; nothing else in the codebase changes.
 */
public interface MetadataStore<M extends TransactionMetadata> {

    TransactionType type();

    Class<M> metadataClass();

    void insert(long transactionId, M metadata);

    M load(long transactionId);

    /** Remove this type's rows for one transaction (used by PATCH metadata = delete + insert). */
    void delete(long transactionId);
}
