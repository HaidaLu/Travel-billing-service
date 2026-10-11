package com.navan.billing.transaction.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.billing.transaction.domain.FeeLine;
import com.navan.billing.transaction.domain.TaxLine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record PatchTransactionRequest(
        Instant occurredAt,
        BigDecimal total,
        List<@Valid @NotNull TaxLine> taxLines,
        List<@Valid @NotNull FeeLine> feeLines,
        JsonNode metadata) {
}
