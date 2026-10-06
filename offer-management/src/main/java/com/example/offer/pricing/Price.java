package com.example.offer.pricing;

import java.time.LocalDate;
import java.util.Objects;

public record Price(String priceId, String productId, Money amount, DateRange validity) {

    public Price {
        Objects.requireNonNull(priceId, "priceId is required");
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(validity, "validity is required");
    }

    public PriceState stateAt(LocalDate date) {
        if (date.isBefore(validity.from())) {
            return PriceState.SCHEDULED;
        }
        return validity.covers(date) ? PriceState.ACTIVE : PriceState.EXPIRED;
    }

    public boolean editableAt(LocalDate date) {
        return stateAt(date) == PriceState.SCHEDULED;
    }
}
