package com.example.offer.draft;

import java.time.Instant;

/**
 * The outcome of {@code POST /review-requests/{id}/approval} and {@code .../rejection}
 * (element 02 screen 06). Exactly one of {@code approvedBy}/{@code rejectedBy} is set —
 * approval and rejection share the shape so the UI switches on {@code status}.
 */
public record ReviewDecisionResponse(String reviewRequestId, String status, String approvedBy,
                                     String rejectedBy, String reason, Instant decidedAt) {

    static ReviewDecisionResponse approved(ReviewRequest decision) {
        return new ReviewDecisionResponse(decision.reviewRequestId(), "APPROVED",
                decision.decidedBy().subject(), null, null, decision.at());
    }

    static ReviewDecisionResponse rejected(ReviewRequest decision, String reason) {
        return new ReviewDecisionResponse(decision.reviewRequestId(), "REJECTED",
                null, decision.decidedBy().subject(), reason, decision.at());
    }
}
