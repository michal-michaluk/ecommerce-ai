package com.example.offer.pricing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record PriceScheduleSnapshot(String productId, List<Price> prices, List<Discount> discounts) {

    public PriceScheduleSnapshot {
        prices = List.copyOf(prices);
        discounts = List.copyOf(discounts);
    }

    /** The Price whose range is ACTIVE on {@code businessDate}, or empty (element 06, RULE-37). */
    public static Optional<Price> activePrice(List<Price> prices, LocalDate businessDate) {
        return prices.stream()
                .filter(price -> price.stateAt(businessDate) == PriceState.ACTIVE)
                .findFirst();
    }

    /** The Discount whose range is ACTIVE on {@code businessDate}, or empty (element 06). */
    public static Optional<Discount> activeDiscount(List<Discount> discounts, LocalDate businessDate) {
        return discounts.stream()
                .filter(discount -> discount.stateAt(businessDate) == PriceState.ACTIVE)
                .findFirst();
    }

    public Optional<Price> activePrice(LocalDate businessDate) {
        return activePrice(prices, businessDate);
    }

    public Optional<Discount> activeDiscount(LocalDate businessDate) {
        return activeDiscount(discounts, businessDate);
    }
}
