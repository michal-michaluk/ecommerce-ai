package com.example.offer.draft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;

/** Null fields mean "leave unchanged" — the PATCH semantics of the draft workspace. */
@Builder
public record SaveDraftCommand(
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String title,
        @Pattern(regexp = ".*\\S.*", message = "must not be blank") String description,
        @Valid Attributes attributes) {

    @Builder
    public record Attributes(String category, String manualUrl) {
    }
}
