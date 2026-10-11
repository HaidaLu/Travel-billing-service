package com.navan.billing.transaction.domain;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Carrier/merchant fee on one transaction (YQ, booking fee). Kept separate from {@link TaxLine} on purpose. */
public record FeeLine(@NotBlank String name, @NotNull BigDecimal amount) {
}
