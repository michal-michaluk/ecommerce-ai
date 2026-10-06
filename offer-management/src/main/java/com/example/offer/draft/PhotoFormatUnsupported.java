package com.example.offer.draft;

public class PhotoFormatUnsupported extends RuntimeException {
    public PhotoFormatUnsupported(String mime) {
        super("unsupported photo format " + mime);
    }
}
