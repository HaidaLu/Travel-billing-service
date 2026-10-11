package com.navan.billing.transaction.domain.metadata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.navan.billing.transaction.domain.TransactionType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record HotelMetadata(
        @NotBlank String name,
        @NotNull @Valid Address address,
        @NotNull LocalDate checkIn,
        @NotNull LocalDate checkOut,
        @Positive int nights,
        @NotNull BigDecimal roomRatePerNight,
        List<@Valid @NotNull HotelExtra> extras) implements TransactionMetadata {

    @JsonIgnore
    @Override
    public TransactionType type() {
        return TransactionType.HOTEL;
    }

    public record Address(@NotBlank String street, @NotBlank String city, @NotBlank String country) {
    }

    public record HotelExtra(@NotBlank String name, @NotNull BigDecimal amount) {
    }
}
