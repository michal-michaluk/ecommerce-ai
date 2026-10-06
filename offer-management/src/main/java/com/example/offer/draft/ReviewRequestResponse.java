package com.example.offer.draft;

import java.time.Instant;

public record ReviewRequestResponse(String reviewRequestId, String productId, String descriptionVersion,
                                    String status, String author, Instant submittedAt, int missingCount) {
}
