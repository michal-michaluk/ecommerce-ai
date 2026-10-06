package com.example.offer.offer;

/**
 * The draft state this context mirrors through events. {@code NONE} is not a value — it means
 * no draft exists and is represented by {@code null} (RULE-1).
 */
public enum DraftState {
    EDITING,
    IN_REVIEW,
    APPROVED
}
