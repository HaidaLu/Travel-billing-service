package com.navan.billing.invoice.domain;

import java.math.BigDecimal;

/** Invoice-level fee total for one fee name. */
public record FeeRollupLine(String name, BigDecimal amount) {
}
