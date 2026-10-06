package com.example.offer.draft;

import java.util.List;

record PhotoFormatPolicy(List<PhotoFormat> allowed) {

    static final long MAX_BYTES = 10L * 1024 * 1024;
    static final int MIN_SIDE = 1000;

    static PhotoFormatPolicy standard() {
        return new PhotoFormatPolicy(List.of(
                new PhotoFormat("image/jpeg", List.of("jpg", "jpeg"), MIN_SIDE, MIN_SIDE, MAX_BYTES),
                new PhotoFormat("image/png", List.of("png"), MIN_SIDE, MIN_SIDE, MAX_BYTES)));
    }

    void check(Photo photo) {
        PhotoFormat format = allowed.stream()
                .filter(f -> f.mime().equals(photo.mime())).findFirst()
                .orElseThrow(() -> new PhotoFormatUnsupported(photo.mime()));
        if (photo.width() < format.minWidth() || photo.height() < format.minHeight()) {
            throw new PhotoTooSmall(photo.width(), photo.height());
        }
        if (photo.sizeBytes() > format.maxBytes()) {
            throw new PhotoTooLarge(photo.sizeBytes());
        }
    }
}
