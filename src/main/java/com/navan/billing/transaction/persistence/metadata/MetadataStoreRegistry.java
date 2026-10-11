package com.navan.billing.transaction.persistence.metadata;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.TransactionMetadata;

/** Routes a {@link TransactionType} to its {@link MetadataStore}. Fails fast at startup if a type has no store. */
@Component
public class MetadataStoreRegistry {

    private final Map<TransactionType, MetadataStore<?>> stores = new EnumMap<>(TransactionType.class);

    public MetadataStoreRegistry(List<MetadataStore<?>> all) {
        for (MetadataStore<?> s : all) {
            if (stores.put(s.type(), s) != null) {
                throw new IllegalStateException("two MetadataStores for " + s.type());
            }
        }
        for (TransactionType t : TransactionType.values()) {
            if (!stores.containsKey(t)) {
                throw new IllegalStateException("no MetadataStore for " + t);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <M extends TransactionMetadata> MetadataStore<M> forType(TransactionType type) {
        return (MetadataStore<M>) stores.get(type);
    }

    public void insert(long transactionId, TransactionMetadata metadata) {
        forType(metadata.type()).insert(transactionId, metadata);
    }

    public TransactionMetadata load(TransactionType type, long transactionId) {
        return forType(type).load(transactionId);
    }
}
