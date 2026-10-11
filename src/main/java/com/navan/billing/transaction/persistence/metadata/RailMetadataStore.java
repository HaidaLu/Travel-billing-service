package com.navan.billing.transaction.persistence.metadata;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.RailMetadata;

/** Tables: rail_legs (ordered by position; a change of train = 2 rows). Follow {@link NavanFeeMetadataStore}; money columns are minor units. */
@Component
class RailMetadataStore implements MetadataStore<RailMetadata> {

    private final JdbcClient jdbc;

    RailMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.RAIL;
    }

    @Override
    public Class<RailMetadata> metadataClass() {
        return RailMetadata.class;
    }

    @Override
    public void insert(long transactionId, RailMetadata metadata) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public RailMetadata load(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void delete(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }
}
