package com.example.offer.draft;

import java.util.Optional;

interface DraftRepository {
    Optional<DescriptionDraft> get(String productId);

    /** The product that owns a review request — the URL of the decision endpoints is keyed by review. */
    Optional<String> productIdOfReview(String reviewRequestId);

    void save(DescriptionDraft draft);
}
