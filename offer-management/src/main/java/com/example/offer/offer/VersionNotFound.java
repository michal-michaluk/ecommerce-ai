package com.example.offer.offer;

public class VersionNotFound extends RuntimeException {

    public VersionNotFound(String version) {
        super("version " + version + " not found");
    }
}
