package com.example.offer.catalog;

import com.example.offer.offer.OfferState;

import java.time.Instant;

/**
 * The catalog list/detail read shape (element 02 screens 01). {@code state} is the derived
 * {@link OfferState}; {@code updatedBy} travels beside {@code updatedAt} (Q35).
 */
record ProductRead(String productId, String category, OfferState state,
                   String visibleVersion, String scheduledVersion,
                   Instant updatedAt, String updatedBy) {
}
