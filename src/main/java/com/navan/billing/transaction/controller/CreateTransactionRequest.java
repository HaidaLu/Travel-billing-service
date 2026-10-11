package com.navan.billing.transaction.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.navan.billing.transaction.dto.FeeLine;
import com.navan.billing.transaction.dto.NewTransaction;
import com.navan.billing.transaction.dto.TaxLine;
import com.navan.billing.transaction.dto.TransactionType;
import com.navan.billing.transaction.dto.metadata.TransactionMetadata;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CreateTransactionRequest(
        @NotNull TransactionType type,
        @NotBlank String externalId,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull Instant occurredAt,
        @NotNull BigDecimal total,
        @NotNull List<@Valid @NotNull TaxLine> taxLines,
        @NotNull List<@Valid @NotNull FeeLine> feeLines,
        @NotNull @Valid
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
                property = "type", visible = true) /**type will decide which metadata will be created */
        TransactionMetadata metadata) {

    public NewTransaction toNewTransaction() {
        return new NewTransaction(type, externalId, currency, occurredAt, total, taxLines, feeLines, metadata);
    }
}
