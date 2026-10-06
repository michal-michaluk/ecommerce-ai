package com.example.offer.offer;

import com.example.offer.auth.Audit;

import java.time.Instant;
import java.util.List;

/**
 * Read-only view of the {@link Product} process aggregate. {@code visibleVersion} and
 * {@code scheduledVersion} are projections of the publications at a business date (RULE-4).
 */
public record ProductSnapshot(String productId, OfferPresence offerPresence,
                              String visibleVersion, String scheduledVersion,
                              DraftState draftState, Instant createdAt,
                              Audit lastChange, List<DescriptionVersion> versions,
                              List<Publication> publications) {

    public ProductSnapshot {
        versions = List.copyOf(versions);
        publications = List.copyOf(publications);
    }
}
