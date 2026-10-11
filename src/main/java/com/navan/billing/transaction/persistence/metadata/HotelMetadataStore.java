package com.navan.billing.transaction.persistence.metadata;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.HotelMetadata;

/** Tables: hotel_details + hotel_extras (address is flattened into hotel_details columns). Follow {@link NavanFeeMetadataStore}; money columns are minor units. */
@Component
class HotelMetadataStore implements MetadataStore<HotelMetadata> {

    private final JdbcClient jdbc;

    HotelMetadataStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public TransactionType type() {
        return TransactionType.HOTEL;
    }

    @Override
    public Class<HotelMetadata> metadataClass() {
        return HotelMetadata.class;
    }

    @Override
    public void insert(long transactionId, HotelMetadata metadata) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public HotelMetadata load(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public void delete(long transactionId) {
        throw new UnsupportedOperationException("TODO");
    }
}
