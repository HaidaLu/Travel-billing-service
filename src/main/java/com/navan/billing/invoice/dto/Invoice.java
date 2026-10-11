package com.navan.billing.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record Invoice(
        long id,
        String currency,
        BigDecimal total,
        List<TransactionSnapshot> lines,
        List<TaxRollupLine> taxRollup,
        List<FeeRollupLine> feeRollup,
        Instant createdAt,
        Instant updatedAt) {
}
