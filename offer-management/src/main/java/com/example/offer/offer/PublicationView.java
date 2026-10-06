package com.example.offer.offer;

import java.time.LocalDate;
import java.util.List;

/** The publication screen read: the quality gate, the visible version and the version history. */
public record PublicationView(String productId, Gate gate, String publishedVersion,
                              LocalDate availableFrom, List<VersionListResponse.VersionItem> versions) {

    public record BlockingItem(String code, String label) {
    }

    public record Gate(boolean passed, List<BlockingItem> blocking) {
        public Gate {
            blocking = List.copyOf(blocking);
        }
    }

    public PublicationView {
        versions = List.copyOf(versions);
    }

    public static PublicationView of(ProductSnapshot snapshot, Completeness completeness,
                                     LocalDate businessDate) {
        Publication visible = snapshot.visibleVersion() == null ? null
                : VersionListResponse.latest(snapshot, snapshot.visibleVersion());
        return new PublicationView(snapshot.productId(),
                new Gate(completeness.complete(), completeness.missing().stream()
                        .map(item -> new BlockingItem(item.code(), item.label())).toList()),
                snapshot.visibleVersion(),
                visible == null ? null : visible.availableFrom(),
                VersionListResponse.items(snapshot, businessDate));
    }
}
