package com.navan.billing.transaction.domain.metadata;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.billing.transaction.domain.TransactionType;

import jakarta.validation.constraints.NotNull;

public record NavanFeeMetadata(
        @NotNull NavanFeeKind feeKind,
        String description,
        String relatedExternalId) implements TransactionMetadata {

    @JsonIgnore
    @Override
    public TransactionType type() {
        return TransactionType.NAVAN_FEE;
    }
}
