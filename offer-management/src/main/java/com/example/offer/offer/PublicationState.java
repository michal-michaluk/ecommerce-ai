package com.example.offer.offer;

/** The lifecycle of a single publication; {@code CANCELLED} is a state, never a delete (RULE-33, A6). */
public enum PublicationState {
    SCHEDULED,
    PUBLISHED,
    CANCELLED
}
