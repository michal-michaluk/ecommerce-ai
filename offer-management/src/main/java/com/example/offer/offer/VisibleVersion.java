package com.example.offer.offer;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Which version the customer sees at a business date (element 04 §9a). Derived, immutable output —
 * never stored, never edited directly (RULE-4, RULE-40).
 */
public record VisibleVersion(Publication publication) {

    public static VisibleVersion none() {
        return new VisibleVersion(null);
    }

    public static VisibleVersion at(OfferPresence presence, List<Publication> publications, LocalDate businessDate) {
        Objects.requireNonNull(presence, "presence is required");
        Objects.requireNonNull(businessDate, "businessDate is required");
        if (presence == OfferPresence.REMOVED) {
            return none();
        }
        return publications.stream()
                .filter(publication -> publication.exposesAt(businessDate))
                // a null availableFrom means "visible immediately", so it is the earliest candidate
                .max(Comparator.comparing(Publication::availableFrom,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(VisibleVersion::new)
                .orElseGet(VisibleVersion::none);
    }

    public boolean isPresent() {
        return publication != null;
    }

    public Optional<Publication> version() {
        return Optional.ofNullable(publication);
    }
}
