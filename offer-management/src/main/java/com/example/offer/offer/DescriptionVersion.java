package com.example.offer.offer;

import com.example.offer.auth.Identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, versioned snapshot created when an approved draft is published (RULE-9, RULE-10). */
public record DescriptionVersion(String productId, String version, String title,
                                 String description, Map<String, String> attributes,
                                 List<String> photoIds, String basedOnVersion,
                                 Identity approvedBy, Instant createdAt) {

    public DescriptionVersion {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(version, "version is required");
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(approvedBy, "approvedBy is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(attributes, "attributes is required");
        Objects.requireNonNull(photoIds, "photoIds is required");
        attributes = Map.copyOf(attributes);
        photoIds = List.copyOf(photoIds);
    }

    /** The lineage: the version this one forks from, or {@code null} for a fresh line (RULE-7, RULE-12). */
    public boolean isReverted() {
        return basedOnVersion != null;
    }
}
