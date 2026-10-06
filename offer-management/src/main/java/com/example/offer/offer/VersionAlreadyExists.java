package com.example.offer.offer;

public class VersionAlreadyExists extends RuntimeException {

    public VersionAlreadyExists(String version) {
        super("version " + version + " already exists");
    }
}
