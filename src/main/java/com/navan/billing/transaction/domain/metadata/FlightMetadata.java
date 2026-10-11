package com.navan.billing.transaction.domain.metadata;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.billing.transaction.domain.TransactionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * {@code coupons} is deliberately not {@code @NotEmpty}: request validation runs before the duplicate check, so the
 * fixture's duplicate_flight ({@code "coupons": []}) would get 400 instead of 409 + existing id. "A flight has at
 * least one coupon" is enforced by TransactionService on rows it is about to write (create after the external_id
 * claim, and PATCH).
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
