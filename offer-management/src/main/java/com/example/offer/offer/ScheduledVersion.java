package com.example.offer.offer;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The next version scheduled to become visible (element 04 §9a). Derived, immutable output — never
 * stored, never edited directly (RULE-4). The earliest future publication wins; a removed product
 * has none.
 */
public record ScheduledVersion(Publication publication) {

    public static ScheduledVersion none() {
        return new ScheduledVersion(null);
    }

    public static ScheduledVersion at(OfferPresence presence, List<Publication> publications, LocalDate businessDate) {
        Objects.requireNonNull(presence, "presence is required");
        Objects.requireNonNull(businessDate, "businessDate is required");
        if (presence == OfferPresence.REMOVED) {
            return none();
        }
        return publications.stream()
                .filter(publication -> publication.stateAt(businessDate) == PublicationState.SCHEDULED)
                .min(Comparator.comparing(Publication::availableFrom))
                .map(ScheduledVersion::new)
                .orElseGet(ScheduledVersion::none);
    }

    public boolean isPresent() {
        return publication != null;
    }

    public Optional<Publication> version() {
        return Optional.ofNullable(publication);
    }
}
