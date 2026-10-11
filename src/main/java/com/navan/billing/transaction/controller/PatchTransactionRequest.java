package com.navan.billing.transaction.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.navan.billing.transaction.domain.FeeLine;
import com.navan.billing.transaction.domain.TaxLine;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;

public record PatchTransactionRequest(
        Instant occurredAt,
        BigDecimal total,
        List<@Valid @NotNull TaxLine> taxLines,
        List<@Valid @NotNull FeeLine> feeLines,
        JsonNode metadata,
        @Null(message = "cannot be changed") JsonNode type,
        @Null(message = "cannot be changed") JsonNode externalId,
        @Null(message = "cannot be changed") JsonNode currency) {
}
