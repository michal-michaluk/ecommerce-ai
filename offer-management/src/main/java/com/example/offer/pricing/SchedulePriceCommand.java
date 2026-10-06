package com.example.offer.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

@Builder
public record SchedulePriceCommand(@NotNull PriceKind kind, @Valid MoneyView amount, String percent,
                                   @NotNull LocalDate validFrom, LocalDate validTo) {

    @AssertTrue(message = "amount is required for a PRICE and percent for a DISCOUNT")
    public boolean isAmountMatchingKind() {
        return kind == PriceKind.PRICE ? amount != null : percent != null;
    }
}
