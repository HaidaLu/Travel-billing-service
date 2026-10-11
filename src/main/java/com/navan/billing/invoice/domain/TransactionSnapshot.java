package com.navan.billing.invoice.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.navan.billing.transaction.domain.FeeLine;
import com.navan.billing.transaction.domain.TaxLine;
import com.navan.billing.transaction.domain.Transaction;
import com.navan.billing.transaction.domain.TransactionType;
import com.navan.billing.transaction.domain.metadata.TransactionMetadata;

/**
 * Frozen copy of a transaction as it was when invoiced. Owned by the invoice and never joined back
 * to the live row. It is its own type (not {@link Transaction}) so the snapshot schema can evolve and be versioned
 * independently of the live model.
 */
public record TransactionSnapshot(
        String transactionId,
        TransactionType type,
        String externalId,
        String currency,
        Instant occurredAt,
        BigDecimal total,
        List<TaxLine> taxLines,
        List<FeeLine> feeLines,
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                property = "type", visible = true)
        TransactionMetadata metadata) {

    public static TransactionSnapshot of(String publicId, Transaction t) {
        return new TransactionSnapshot(publicId, t.type(), t.externalId(), t.currency(), t.occurredAt(),
                t.total(), List.copyOf(t.taxLines()), List.copyOf(t.feeLines()), t.metadata());
    }
}
