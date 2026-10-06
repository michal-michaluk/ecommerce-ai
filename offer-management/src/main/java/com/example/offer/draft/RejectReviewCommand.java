package com.example.offer.draft;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/** The body of {@code POST /review-requests/{id}/rejection}: the reviewer's reason. */
@Builder
public record RejectReviewCommand(@NotBlank String reason) {
}
