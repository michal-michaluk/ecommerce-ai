package com.example.offer.auth;

/** Authenticated subject, built in the adapter from the JWT — never inside the domain. */
public record Identity(String subject) {
    public Identity {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("identity is required");
        }
    }
}
