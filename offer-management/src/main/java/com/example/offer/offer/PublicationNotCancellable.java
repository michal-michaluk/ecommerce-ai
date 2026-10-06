package com.example.offer.offer;

public class PublicationNotCancellable extends RuntimeException {

    public PublicationNotCancellable(String publicationId) {
        super("publication " + publicationId + " is not scheduled and cannot be cancelled");
    }
}
