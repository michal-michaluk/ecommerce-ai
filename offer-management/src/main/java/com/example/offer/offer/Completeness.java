package com.example.offer.offer;

import java.util.List;

/** The derived review-completeness output: only unmet catalogue items, in catalogue order (RULE-42, RULE-43). */
public record Completeness(boolean complete, List<MissingRequirement> missing, List<TextIssue> issues) {

    public record MissingRequirement(String code, String label) {
    }

    public Completeness {
        missing = List.copyOf(missing);
        issues = List.copyOf(issues);
    }
}
