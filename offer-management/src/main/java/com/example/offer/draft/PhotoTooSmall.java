package com.example.offer.draft;

public class PhotoTooSmall extends RuntimeException {
    public PhotoTooSmall(int width, int height) {
        super("photo " + width + "x" + height + " is below the minimum side");
    }
}
