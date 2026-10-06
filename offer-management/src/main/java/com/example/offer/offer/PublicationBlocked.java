package com.example.offer.offer;

import java.util.List;

public class PublicationBlocked extends RuntimeException {

    public PublicationBlocked(List<String> missingRequirements) {
        super("publication blocked by missing requirements " + missingRequirements);
    }
}
