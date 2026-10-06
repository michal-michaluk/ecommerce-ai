package com.example.offer.draft;

public record Description(String value) {
    public Description {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("description is required");
        }
    }
}
