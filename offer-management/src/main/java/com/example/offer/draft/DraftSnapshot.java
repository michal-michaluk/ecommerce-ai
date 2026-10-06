package com.example.offer.draft;

import com.example.offer.auth.Audit;

import java.util.List;

public record DraftSnapshot(String productId, String version, DraftState state, int revision,
                            Title title, Description description, DraftAttributes attributes,
                            List<Photo> photos, String basedOnVersion, ReviewRequest review,
                            Audit lastChange) {
    public DraftSnapshot {
        photos = List.copyOf(photos);
    }
}
