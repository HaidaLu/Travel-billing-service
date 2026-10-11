package com.navan.billing.transaction.domain.metadata;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** One flown segment. A round trip has at least two. */
public record FlightCoupon(
        @NotBlank String flightNumber,
        String carrier,
        @NotBlank String from,
        @NotBlank String to,
        @NotNull Instant departure,
        @NotNull Instant arrival) {
}
