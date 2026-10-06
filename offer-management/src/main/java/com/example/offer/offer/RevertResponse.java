package com.example.offer.offer;

import java.time.Instant;

/** The draft opened by revert — it never rewinds history (RULE-12, property P4). */
public record RevertResponse(String productId, String version, String state, String basedOnVersion,
                             Instant lastSavedAt, String updatedBy) {
}
