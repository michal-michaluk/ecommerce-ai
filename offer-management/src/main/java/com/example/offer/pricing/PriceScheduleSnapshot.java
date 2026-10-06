package com.example.offer.pricing;

import java.util.List;

public record PriceScheduleSnapshot(String productId, List<Price> prices, List<Discount> discounts) {

    public PriceScheduleSnapshot {
        prices = List.copyOf(prices);
        discounts = List.copyOf(discounts);
    }
}
