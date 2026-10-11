package com.navan.billing.transaction.persistence.metadata;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.FlightMetadata;

/** Tables: flight_details + flight_coupons (ordered by position). Follow {@link NavanFeeMetadataStore}; money columns are minor units. */
@Component
class FlightMetadataStore implements MetadataStore<FlightMetadata> {

    private final JdbcClient jdbc;

    FlightMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.FLIGHT;
    }

    @Override
    public Class<FlightMetadata> metadataClass() {
        return FlightMetadata.class;
    }

    @Override
    public void insert(long transactionId, FlightMetadata metadata) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public FlightMetadata load(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void delete(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }
}
