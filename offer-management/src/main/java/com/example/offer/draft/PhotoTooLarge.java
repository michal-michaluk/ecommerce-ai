package com.example.offer.draft;

public class PhotoTooLarge extends RuntimeException {
    public PhotoTooLarge(long sizeBytes) {
        super("photo of " + sizeBytes + " bytes exceeds the maximum size");
    }
}
