package com.navan.billing.invoice.dto;

import java.math.BigDecimal;

/** Invoice-level tax total for one (name, rate) group. */
public record TaxRollupLine(String name, BigDecimal rate, BigDecimal amount) {
}
