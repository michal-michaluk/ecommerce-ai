package com.example.offer.offer;

import java.util.List;

/** An advisory finding of the text-check port — always advisory, never blocking (RULE-44, RULE-48). */
public record TextIssue(String message, Severity severity) {

    public enum Severity { ADVISORY }

    public TextIssue {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message is required");
        }
    }
}
