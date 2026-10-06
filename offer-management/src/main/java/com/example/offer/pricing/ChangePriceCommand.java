package com.example.offer.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.time.LocalDate;

/** The entry's kind is taken from the stored entry; only the values and range change. */
@Builder
public record ChangePriceCommand(@Valid MoneyView amount, String percent,
                                 @NotNull LocalDate validFrom, LocalDate validTo) {
}
