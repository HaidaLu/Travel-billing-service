package com.navan.billing.invoice.domain;

import java.math.BigDecimal;
import java.util.List;

/** Everything an invoice stores, computed in memory before the single atomic write. */
public record InvoiceDraft(
        String currency,
        BigDecimal total,
        List<TransactionSnapshot> lines,
        List<TaxRollupLine> taxRollup,
        List<FeeRollupLine> feeRollup) {
}
