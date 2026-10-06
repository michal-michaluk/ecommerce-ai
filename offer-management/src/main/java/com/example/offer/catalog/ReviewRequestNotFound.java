package com.example.offer.catalog;

/** Raised when a review request has no read model — mapped to {@code 404 REVIEW_NOT_FOUND}. */
class ReviewRequestNotFound extends RuntimeException {

    private final String reviewRequestId;

    ReviewRequestNotFound(String reviewRequestId) {
        super("review request " + reviewRequestId + " not found");
        this.reviewRequestId = reviewRequestId;
    }

    String reviewRequestId() {
        return reviewRequestId;
    }
}
