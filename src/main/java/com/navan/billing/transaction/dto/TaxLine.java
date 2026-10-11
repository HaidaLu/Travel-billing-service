package com.navan.billing.transaction.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Government/VAT-style tax on one transaction. {@code rate} is optional (e.g. flat airport tax). */
public record TaxLine(@NotBlank String name, BigDecimal rate, @NotNull BigDecimal amount) {
}
