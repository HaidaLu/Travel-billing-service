package com.navan.billing.invoice.dto;

import java.math.BigDecimal;

/** Invoice-level fee total for one fee name. */
public record FeeRollupLine(String name, BigDecimal amount) {
}
