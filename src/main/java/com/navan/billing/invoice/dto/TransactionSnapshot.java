package com.navan.billing.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.navan.billing.transaction.dto.FeeLine;
import com.navan.billing.transaction.dto.TaxLine;
import com.navan.billing.transaction.dto.Transaction;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.TransactionMetadata;

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

    // copy on write
    // invoice data and transaction data will be irrelevant
    public static TransactionSnapshot of(String publicId, Transaction t) {
        return new TransactionSnapshot(publicId, t.type(), t.externalId(), t.currency(), t.occurredAt(),
                t.total(), List.copyOf(t.taxLines()), List.copyOf(t.feeLines()), t.metadata());
    }
}
