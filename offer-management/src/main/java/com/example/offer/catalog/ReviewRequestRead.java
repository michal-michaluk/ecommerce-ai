package com.example.offer.catalog;

import java.time.Instant;

/**
 * The review queue/detail read shape (element 02 screens 05/06). {@code submittedAt} is preserved
 * from the submission moment even after the review is decided (Q34).
 */
record ReviewRequestRead(String reviewRequestId, String productId, String productTitle,
                         String descriptionVersion, String status, String author,
                         Instant submittedAt, int missingCount,
                         String decidedBy, Instant decidedAt) {
}
