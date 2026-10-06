package com.example.offer.offer;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record VersionListResponse(String productId, List<VersionItem> items) {

    public record VersionItem(String version, String state, LocalDate availableFrom, Instant publishedAt,
                              String basedOnVersion, Instant createdAt) {
    }

    public VersionListResponse {
        items = List.copyOf(items);
    }

    public static VersionListResponse of(ProductSnapshot snapshot, LocalDate businessDate) {
        return new VersionListResponse(snapshot.productId(), items(snapshot, businessDate));
    }

    static List<VersionItem> items(ProductSnapshot snapshot, LocalDate businessDate) {
        return snapshot.versions().stream()
                .map(version -> item(version, snapshot, businessDate))
                .toList();
    }

    private static VersionItem item(DescriptionVersion version, ProductSnapshot snapshot,
                                    LocalDate businessDate) {
        Publication publication = latest(snapshot, version.version());
        return new VersionItem(version.version(), state(version, snapshot, businessDate),
                publication == null ? null : publication.availableFrom(),
                publication == null ? null : publication.exposedAt(),
                version.basedOnVersion(), version.createdAt());
    }

    private static String state(DescriptionVersion version, ProductSnapshot snapshot, LocalDate businessDate) {
        Publication publication = latest(snapshot, version.version());
        if (publication == null || publication.cancelled()) {
            return publication == null ? "SCHEDULED" : "SUPERSEDED";
        }
        return switch (publication.stateAt(businessDate)) {
            case SCHEDULED -> "SCHEDULED";
            case PUBLISHED -> version.version().equals(snapshot.visibleVersion()) ? "PUBLISHED" : "SUPERSEDED";
            case CANCELLED -> "SUPERSEDED";
        };
    }

    static Publication latest(ProductSnapshot snapshot, String version) {
        return snapshot.publications().stream()
                .filter(publication -> publication.version().equals(version))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
