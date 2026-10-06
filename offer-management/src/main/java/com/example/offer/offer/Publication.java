package com.example.offer.offer;

import com.example.offer.auth.Audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Pins exactly one version and decides when it becomes visible (RULE-29). Append-only (RULE-33):
 * a cancellation is a new state on the same publication, never a delete.
 */
public record Publication(String publicationId, String productId, String version,
                          LocalDate availableFrom, Instant createdAt, Instant exposedAt,
                          boolean cancelled) {

    public Publication {
        Objects.requireNonNull(publicationId, "publicationId is required");
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(version, "version is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
    }

    static Publication schedule(String publicationId, String productId, String version,
                                LocalDate availableFrom, LocalDate businessDate, Audit audit) {
        boolean future = availableFrom != null && availableFrom.isAfter(businessDate);
        return new Publication(publicationId, productId, version, availableFrom, audit.at(),
                future ? null : audit.at(), false);
    }

    /**
     * The state at a business date — derived, so a scheduled publication becomes visible on its
     * own date without a stored flag going stale (D4).
     */
    public PublicationState stateAt(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate is required");
        if (cancelled) {
            return PublicationState.CANCELLED;
        }
        return availableFrom != null && availableFrom.isAfter(businessDate)
                ? PublicationState.SCHEDULED
                : PublicationState.PUBLISHED;
    }

    public boolean exposesAt(LocalDate businessDate) {
        return stateAt(businessDate) == PublicationState.PUBLISHED;
    }

    /** Cancelling a scheduled publication is a new state, never a delete (RULE-33, A6). */
    Publication cancel(LocalDate businessDate) {
        if (stateAt(businessDate) != PublicationState.SCHEDULED) {
            throw new PublicationNotCancellable(publicationId);
        }
        return new Publication(publicationId, productId, version, availableFrom, createdAt,
                exposedAt, true);
    }
}
