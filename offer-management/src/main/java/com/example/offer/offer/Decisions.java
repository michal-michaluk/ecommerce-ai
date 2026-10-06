package com.example.offer.offer;

import com.example.offer.auth.Identity;
import com.example.offer.tools.ErrorCode;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The five automated decisions of element 07. Each returns an allow/deny; a deny carries the exact
 * element-02 error code (RULE-58). Pure and deterministic — no clock, no repository (RULE-59).
 */
public final class Decisions {

    public enum QualityOutcome { PASS, PASS_WITH_ISSUES, FAIL }

    /** D1: only {@code missing[]} can block; advisory issues are reported, never blocking (RULE-48). */
    public record Quality(QualityOutcome outcome, List<Completeness.MissingRequirement> blocking) {
        public Quality {
            blocking = List.copyOf(blocking);
        }
    }

    /** The decision result: allowed when {@code denial} is {@code null}. */
    public record Decision<T>(T value, ErrorCode denial, List<Completeness.MissingRequirement> blocking) {
        public Decision {
            blocking = blocking == null ? List.of() : List.copyOf(blocking);
        }

        public boolean isAllowed() {
            return denial == null;
        }
    }

    public Quality quality(Completeness completeness) {
        if (!completeness.missing().isEmpty()) {
            return new Quality(QualityOutcome.FAIL, completeness.missing());
        }
        return new Quality(completeness.issues().isEmpty()
                ? QualityOutcome.PASS : QualityOutcome.PASS_WITH_ISSUES, List.of());
    }

    /** D2: the most specific failure wins — not-found, then not-approved, then blocked (RULE-50). */
    public Decision<Void> publishGuard(boolean versionExists, boolean versionApproved, Completeness completeness) {
        if (!versionExists) {
            return deny(ErrorCode.VERSION_NOT_FOUND);
        }
        if (!versionApproved) {
            return deny(ErrorCode.VERSION_NOT_APPROVED);
        }
        if (!completeness.complete()) {
            return new Decision<>(null, ErrorCode.PUBLICATION_BLOCKED, completeness.missing());
        }
        return allow();
    }

    /** D3: requesting a review with missing items is allowed — the count travels, it never blocks (RULE-52). */
    public Decision<Integer> reviewRequestGuard(boolean draftExists, boolean inReview, int missingCount) {
        if (!draftExists) {
            return new Decision<>(null, ErrorCode.PRODUCT_NOT_FOUND, List.of());
        }
        if (inReview) {
            return new Decision<>(null, ErrorCode.REVIEW_ALREADY_PENDING, List.of());
        }
        return new Decision<>(missingCount, null, List.of());
    }

    /** D4: null/today/past publish now, a future date schedules, before creation is invalid (T1–T4). */
    public Decision<PublicationState> publicationTiming(LocalDate availableFrom, LocalDate businessDate,
                                                        LocalDate productCreatedOn) {
        if (availableFrom != null && productCreatedOn != null && availableFrom.isBefore(productCreatedOn)) {
            return new Decision<>(null, ErrorCode.VALIDATION_FAILED, List.of());
        }
        PublicationState state = availableFrom != null && availableFrom.isAfter(businessDate)
                ? PublicationState.SCHEDULED
                : PublicationState.PUBLISHED;
        return new Decision<>(state, null, List.of());
    }

    /** D5: evaluated on the decision, never on the queue read; the reviewer is never the author (RULE-56). */
    public Decision<Void> separationOfDuties(boolean reviewPending, Identity author, Identity decidedBy) {
        if (!reviewPending) {
            return deny(ErrorCode.REVIEW_NOT_PENDING);
        }
        if (Objects.equals(author, decidedBy)) {
            return deny(ErrorCode.REVIEWER_IS_AUTHOR);
        }
        return allow();
    }

    private static <T> Decision<T> allow() {
        return new Decision<>(null, null, List.of());
    }

    private static <T> Decision<T> deny(ErrorCode code) {
        return new Decision<>(null, code, List.of());
    }
}
