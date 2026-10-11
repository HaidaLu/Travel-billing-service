package com.navan.billing.transaction.dto.metadata;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.navan.billing.transaction.dto.TransactionType;

/**
 * Type-specific details. Sealed, so a flight can never be stored as a hotel blob and a {@code switch}
 * over subtypes is exhaustive.
 *
 * <p>The subtype is chosen by the <em>sibling</em> {@code type} field on the enclosing object, not by a field
 * inside metadata. Holders declare that with
 * {@code @JsonTypeInfo(use = NAME, include = EXTERNAL_PROPERTY, property = "type", visible = true)} on their
 * {@code metadata} property. Names below must match {@link TransactionType#wire()}.
 */
@JsonSubTypes({
        @JsonSubTypes.Type(value = FlightMetadata.class, name = "flight"),
        @JsonSubTypes.Type(value = HotelMetadata.class, name = "hotel"),
        @JsonSubTypes.Type(value = RailMetadata.class, name = "rail"),
        @JsonSubTypes.Type(value = NavanFeeMetadata.class, name = "navan_fee"),
})
public sealed interface TransactionMetadata
        permits FlightMetadata, HotelMetadata, RailMetadata, NavanFeeMetadata {

    TransactionType type();
}
