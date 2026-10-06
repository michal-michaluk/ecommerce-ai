package com.example.offer.pricing;

public class PriceOverlap extends RuntimeException {

    public PriceOverlap(String productId, DateRange validity) {
        super("entry " + validity + " overlaps an existing entry for product " + productId);
    }
}
