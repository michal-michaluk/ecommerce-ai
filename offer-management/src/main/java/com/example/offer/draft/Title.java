package com.example.offer.draft;

public record Title(String value) {
    public Title {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("title is required");
    }
}
