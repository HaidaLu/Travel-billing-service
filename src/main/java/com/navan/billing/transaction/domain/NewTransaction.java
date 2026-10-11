package com.navan.billing.transaction.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.billing.transaction.domain.metadata.TransactionMetadata;

/** specific type data */
/** A validated transaction that has not been persisted yet (no id, no audit timestamps). */
public record NewTransaction(
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata) {
}
