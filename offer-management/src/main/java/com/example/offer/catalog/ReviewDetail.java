package com.example.offer.catalog;

import com.example.offer.offer.Completeness;

import java.time.Instant;
import java.util.List;

/**
 * The review detail read (element 02 screen 06): the quality-gate result beside the preview at the
 * decision moment. {@code publicationBlocked} mirrors "the gate reports at least one item", so the
 * reviewer sees exactly what publish would refuse (RULE-48).
 */
record ReviewDetail(String reviewRequestId, String productId, String descriptionVersion, String status,
                    String author, Instant submittedAt, Gate gate, Preview preview, Decision decision) {

    record Gate(boolean publicationBlocked, List<Completeness.MissingRequirement> missing, List<Issue> issues) {
        Gate {
            missing = List.copyOf(missing);
            issues = List.copyOf(issues);
        }
    }

    /** An advisory item — reported, never blocking (RULE-48). */
    record Issue(String severity, String message) {
    }

    record Preview(String title, String description, int wordCount, int photoCount, Object price) {
    }

    record Decision(String outcome, String decidedBy, Instant decidedAt, String reason) {
    }
}
