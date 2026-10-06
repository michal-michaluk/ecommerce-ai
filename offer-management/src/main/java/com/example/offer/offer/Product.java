package com.example.offer.offer;

import com.example.offer.auth.Audit;
import lombok.AllArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The process aggregate of the offer context: it coordinates versions, publications and
 * offer-presence, and owns every transition of element 04 RULE-1..4. Package-private, no getters.
 */
@AllArgsConstructor
class Product {

    private final String productId;
    final List<DomainEvent> events;

    private OfferPresence offerPresence;
    private DraftState draftState;
    private List<DescriptionVersion> versions;
    private List<Publication> publications;
    private Instant createdAt;
    private Audit lastChange;

    static Product newProduct(String productId, Audit audit) {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(audit, "audit is required");
        return new Product(productId, new ArrayList<>(), OfferPresence.PRESENT, DraftState.EDITING,
                List.of(), List.of(), audit.at(), audit);
    }

    /** Mirrors a draft-state change; private to this context and therefore emits no boundary event. */
    void changeDraftState(DraftState state, Audit audit) {
        if (state == draftState) {
            return;
        }
        this.draftState = state;
        this.lastChange = audit;
    }

    /** Publishes an approved, complete version: pins it and emits the boundary event (RULE-10, RULE-29, RULE-30). */
    void publish(String publicationId, DescriptionVersion version, LocalDate availableFrom,
                 LocalDate businessDate, List<String> missingRequirements, Audit audit) {
        checkApproved(version);
        checkComplete(missingRequirements);
        checkVersionIsNext(version);
        checkVersionExists(version.basedOnVersion());
        this.versions = append(versions, version);
        this.publications = append(publications, Publication.schedule(publicationId, productId,
                version.version(), availableFrom, businessDate, audit));
        this.draftState = null;
        this.lastChange = audit;
        events.add(new DomainEvent.ProductVersionPublishedToOffer(productId, version.version(),
                availableFrom, audit));
    }

    /** Revert opens a new draft based on an older version — never a rewrite (RULE-12). */
    void revert(String newVersion, String basedOnVersion, Audit audit) {
        checkVersionExists(basedOnVersion);
        this.draftState = DraftState.EDITING;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionReverted(productId, newVersion, basedOnVersion, audit));
    }

    /** Cancelling a scheduled publication keeps it as a new state (RULE-33, A6). */
    void cancelPublication(String publicationId, LocalDate businessDate, Audit audit) {
        Publication cancelled = publicationById(publicationId).cancel(businessDate);
        this.publications = publications.stream()
                .map(publication -> publication.publicationId().equals(publicationId) ? cancelled : publication)
                .toList();
        this.lastChange = audit;
    }

    /** Removal is idempotent and never deletes a version or a publication (RULE-2, RULE-3). */
    void removeFromOffer(Audit audit) {
        if (offerPresence == OfferPresence.REMOVED) {
            return;
        }
        this.offerPresence = OfferPresence.REMOVED;
        this.lastChange = audit;
        events.add(new DomainEvent.ProductRemovedFromOffer(productId, audit));
    }

    Optional<Publication> visibleVersionAt(LocalDate businessDate) {
        return VisibleVersion.at(offerPresence, publications, businessDate).version();
    }

    Optional<Publication> scheduledVersionAt(LocalDate businessDate) {
        if (offerPresence == OfferPresence.REMOVED) {
            return Optional.empty();
        }
        return publications.stream()
                .filter(publication -> publication.stateAt(businessDate) == PublicationState.SCHEDULED)
                .min(Comparator.comparing(Publication::availableFrom));
    }

    /** Element 04 §10 — derived, never stored; the missing requirements are handed in (RULE-34, RULE-70). */
    OfferState offerState(LocalDate businessDate, List<String> missingRequirements) {
        if (offerPresence == OfferPresence.REMOVED) {
            return OfferState.REMOVED;
        }
        if (visibleVersionAt(businessDate).isPresent()) {
            return OfferState.PUBLISHED;
        }
        if (scheduledVersionAt(businessDate).isPresent()) {
            return OfferState.SCHEDULED;
        }
        if (draftState == DraftState.IN_REVIEW) {
            return OfferState.PENDING_REVIEW;
        }
        if (draftState == DraftState.APPROVED && !missingRequirements.isEmpty()) {
            return OfferState.BLOCKED;
        }
        return OfferState.DRAFT;
    }

    ProductSnapshot toSnapshot(LocalDate businessDate) {
        return new ProductSnapshot(productId, offerPresence,
                visibleVersionAt(businessDate).map(Publication::version).orElse(null),
                scheduledVersionAt(businessDate).map(Publication::version).orElse(null),
                draftState, createdAt, lastChange, versions, publications);
    }

    private void checkApproved(DescriptionVersion version) {
        if (draftState != DraftState.APPROVED) {
            throw new VersionNotApproved(version.version());
        }
    }

    private void checkComplete(List<String> missingRequirements) {
        if (!missingRequirements.isEmpty()) {
            throw new PublicationBlocked(missingRequirements);
        }
    }

    private void checkVersionIsNext(DescriptionVersion version) {
        int next = versions.size() + 1;
        if (versionNumber(version.version()) != next) {
            throw new VersionAlreadyExists(version.version());
        }
    }

    private void checkVersionExists(String version) {
        if (version == null) {
            return;
        }
        boolean exists = versions.stream().anyMatch(known -> known.version().equals(version));
        if (!exists) {
            throw new VersionNotFound(version);
        }
    }

    private Publication publicationById(String publicationId) {
        return publications.stream()
                .filter(publication -> publication.publicationId().equals(publicationId))
                .findFirst()
                .orElseThrow(() -> new PublicationNotFound(publicationId));
    }

    private static int versionNumber(String version) {
        if (version == null || version.length() < 2 || version.charAt(0) != 'v') {
            throw new IllegalArgumentException("version must look like v<n>, got " + version);
        }
        try {
            return Integer.parseInt(version.substring(1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version must look like v<n>, got " + version);
        }
    }

    private static <T> List<T> append(List<T> values, T value) {
        return Stream.concat(values.stream(), Stream.of(value)).toList();
    }
}
