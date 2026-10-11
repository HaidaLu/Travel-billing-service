package com.navan.billing.transaction.domain.metadata;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RailLeg(
        @NotBlank String origin,
        @NotBlank String destination,
        @NotNull Instant departure,
        @NotNull Instant arrival,
        String trainNumber,
        @JsonProperty("class") String travelClass) {
}
