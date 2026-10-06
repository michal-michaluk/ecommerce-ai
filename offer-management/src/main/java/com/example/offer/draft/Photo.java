package com.example.offer.draft;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record Photo(String photoId, String fileName, String mime,
                    int width, int height, long sizeBytes, int position, Instant uploadedAt) {
}
