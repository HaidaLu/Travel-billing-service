package com.navan.billing.transaction.dto.metadata;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.billing.transaction.dto.TransactionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/** A journey is a list of legs; a change of train is two legs, not one OD pair. */
public record RailMetadata(@NotEmpty List<@Valid @NotNull RailLeg> legs) implements TransactionMetadata {

    @JsonIgnore
    @Override
    public TransactionType type() {
        return TransactionType.RAIL;
    }
}
