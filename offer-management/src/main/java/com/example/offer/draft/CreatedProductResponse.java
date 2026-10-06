package com.example.offer.draft;

import java.time.Instant;

/** The product created by {@code POST /products} — a blank draft, so no published version or price yet. */
public record CreatedProductResponse(String productId, String title, String category, String state,
                                     String descriptionVersion, String publishedVersion,
                                     String availableFrom, MoneyResponse activePrice,
                                     String activeDiscountPercent, int photoCount,
                                     Instant createdAt, Instant updatedAt) {

    public record MoneyResponse(String value, String currency) {
    }
}
