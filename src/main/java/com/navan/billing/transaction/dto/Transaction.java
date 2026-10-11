package com.navan.billing.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.navan.billing.transaction.dto.metadata.TransactionMetadata;

/** A stored transaction, fully hydrated (header + tax/fee lines + typed metadata). */
public record Transaction(
        long id,
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
}
