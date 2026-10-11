package com.navan.billing.transaction.domain.metadata;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.billing.transaction.domain.TransactionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/*
 * Decision to make: should `coupons` be @NotEmpty?
 * The fixture `duplicate_flight` has "coupons": []. If validation runs before the identity check,
 * that duplicate POST returns 400 instead of 409-with-existing-id. Decide the order (or the rule) on purpose.
 */
public record FlightMetadata(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull BigDecimal fare,
        @NotNull List<@Valid @NotNull FlightCoupon> coupons) implements TransactionMetadata {

    @JsonIgnore
    @Override
    public TransactionType type() {
        return TransactionType.FLIGHT;
    }
}
