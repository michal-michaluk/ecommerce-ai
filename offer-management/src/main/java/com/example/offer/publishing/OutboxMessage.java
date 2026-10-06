package com.example.offer.publishing;

/** A committed, not-yet-published outbox row as the relay consumes it. */
public record OutboxMessage(long id, String eventType, String partitionKey, String payload) {
}
