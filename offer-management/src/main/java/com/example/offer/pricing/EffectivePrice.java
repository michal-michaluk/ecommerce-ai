package com.example.offer.pricing;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public record EffectivePrice(Money amount, String basePriceId, String discountId) {

    /** Element 06 — the business date, never a raw instant: a range is calendar-based. */
    public static Optional<EffectivePrice> of(List<Price> prices, List<Discount> discounts, LocalDate at) {
        Optional<Price> base = prices.stream()
                .filter(price -> price.validity().covers(at))
                .findFirst();                                                       // RULE-37
        if (base.isEmpty()) {
            return Optional.empty();                                            // RULE-39
        }

        Optional<Discount> discount = discounts.stream()
                .filter(d -> d.validity().covers(at))
                .findFirst();

        Money amount = discount
                .map(d -> base.get().amount().discountedBy(d.percent()))        // RULE-46
                .orElseGet(() -> base.get().amount());                          // RULE-45

        return Optional.of(new EffectivePrice(amount, base.get().priceId(),
                discount.map(Discount::discountId).orElse(null)));
    }
}
