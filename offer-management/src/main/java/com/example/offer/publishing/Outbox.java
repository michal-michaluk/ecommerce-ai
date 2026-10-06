package com.example.offer.publishing;

/**
 * The transactional outbox port: producers append an integration event inside their own
 * transaction, the relay delivers it afterwards. A relay failure can therefore never block
 * the write transaction (element 03 "Failure").
 */
public interface Outbox {

    /**
     * Appends the event in the caller's current transaction. Returns {@code false} when an
     * equivalent event was already appended (idempotent at-least-once), {@code true} otherwise.
     */
    boolean append(IntegrationEvent event);
}
