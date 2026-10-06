package com.example.offer.offer;

public class VersionNotApproved extends RuntimeException {

    public VersionNotApproved(String version) {
        super("version " + version + " is not approved for publication");
    }
}
