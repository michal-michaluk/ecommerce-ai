package com.example.offer.draft;

import java.time.Instant;

public record PhotoResponse(String photoId, String fileName, String mime, int width, int height,
                            long sizeBytes, int position, Instant uploadedAt) {

    public static PhotoResponse of(Photo photo) {
        return new PhotoResponse(photo.photoId(), photo.fileName(), photo.mime(), photo.width(),
                photo.height(), photo.sizeBytes(), photo.position(), photo.uploadedAt());
    }
}
