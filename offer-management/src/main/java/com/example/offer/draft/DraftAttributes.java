package com.example.offer.draft;

import lombok.Builder;

@Builder(toBuilder = true)
public record DraftAttributes(String category, String manualUrl) {
    static DraftAttributes empty() {
        return new DraftAttributes(null, null);
    }
}
