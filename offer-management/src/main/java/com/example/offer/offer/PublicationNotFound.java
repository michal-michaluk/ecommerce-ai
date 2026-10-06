package com.example.offer.offer;

public class PublicationNotFound extends RuntimeException {

    public PublicationNotFound(String publicationId) {
        super("publication " + publicationId + " not found");
    }
}
