package com.example.offer.catalog;

import com.example.offer.offer.OfferState;
import com.example.offer.pricing.MoneyView;

import java.time.Instant;
import java.time.LocalDate;

/**
 * The catalog list/detail read shape (element 02 screen 01). {@code state} is the derived
 * {@link OfferState}; {@code publishedVersion} is the visible version; {@code updatedBy} travels
 * beside {@code updatedAt} (Q35).
 */
record ProductRead(String productId, String title, String category, OfferState state,
                  String descriptionVersion, String publishedVersion, LocalDate availableFrom,
                  MoneyView activePrice, String activeDiscountPercent, int photoCount,
                  Instant createdAt, Instant updatedAt, String updatedBy) {
}
