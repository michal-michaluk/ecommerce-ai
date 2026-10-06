package com.example.offer.draft;

import com.example.offer.offer.Completeness;

import java.time.Instant;
import java.util.List;

/**
 * The draft workspace read: the draft plus the server-computed {@code completeness} (P1) and the
 * advisory {@code issues[]} beside it (A7). The UI never polices the rules itself.
 */
public record DescriptionDraftResponse(String productId, String version, String state, String title,
                                       String description, Attributes attributes,
                                       List<PhotoResponse> photos, CompletenessResponse completeness,
                                       List<Issue> issues, Instant lastSavedAt, String updatedBy,
                                       String reviewRequestId) {

    public record Attributes(String category, String manualUrl) {
    }

    public record CompletenessResponse(boolean complete, List<MissingRequirement> missing) {
        public CompletenessResponse {
            missing = List.copyOf(missing);
        }
    }

    public record MissingRequirement(String code, String label) {
    }

    public record Issue(String severity, String message) {
    }

    public DescriptionDraftResponse {
        photos = List.copyOf(photos);
        issues = List.copyOf(issues);
    }

    public static DescriptionDraftResponse of(DraftSnapshot draft, Completeness completeness) {
        return new DescriptionDraftResponse(
                draft.productId(), draft.version(), draft.state().name(),
                draft.title() == null ? null : draft.title().value(),
                draft.description() == null ? null : draft.description().value(),
                draft.attributes() == null
                        ? new Attributes(null, null)
                        : new Attributes(draft.attributes().category(), draft.attributes().manualUrl()),
                draft.photos().stream().map(PhotoResponse::of).toList(),
                new CompletenessResponse(completeness.complete(), completeness.missing().stream()
                        .map(item -> new MissingRequirement(item.code(), item.label())).toList()),
                completeness.issues().stream()
                        .map(issue -> new Issue(issue.severity().name(), issue.message())).toList(),
                draft.lastChange().at(), draft.lastChange().who().subject(),
                draft.review() != null && draft.review().isPending() ? draft.review().reviewRequestId() : null);
    }
}
