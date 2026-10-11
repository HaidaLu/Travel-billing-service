package com.navan.billing.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.billing.common.id.PublicId;
import com.navan.billing.transaction.domain.FeeLine;
import com.navan.billing.transaction.domain.TaxLine;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.TransactionMetadata;

public record TransactionResponse(
        String id,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        TransactionMetadata metadata,
        Instant createdAt,
        Instant updatedAt) {

    public static TransactionResponse from(Transaction t) {
        return new TransactionResponse(PublicId.TRANSACTION.encode(t.id()), t.type(), t.externalId(), t.currency(),
                t.occurredAt(), t.total(), t.taxLines(), t.feeLines(), t.metadata(), t.createdAt(), t.updatedAt());
    }
}
