package com.example.offer.offer;

/** The derived, display-facing summary of the product (element 04 §10, RULE-34/35). */
public enum OfferState {
    REMOVED,
    PUBLISHED,
    SCHEDULED,
    PENDING_REVIEW,
    BLOCKED,
    DRAFT;

    /**
     * The total precedence of element 04 §10 (RULE-34, RULE-35, RULE-70) — the single derivation
     * shared by the {@code Product} aggregate and the catalog read model.
     */
    public static OfferState derive(OfferPresence presence, boolean visible, boolean scheduled,
                                    boolean inReview, boolean approved, boolean complete) {
        if (presence == OfferPresence.REMOVED) {
            return REMOVED;
        }
        if (visible) {
            return PUBLISHED;
        }
        if (scheduled) {
            return SCHEDULED;
        }
        if (inReview) {
            return PENDING_REVIEW;
        }
        if (approved && !complete) {
            return BLOCKED;
        }
        return DRAFT;
    }
}
