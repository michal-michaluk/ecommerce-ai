package com.example.offer.publishing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;

/**
 * Delivers committed outbox rows to Kafka, at-least-once, keyed by {@code productId} so one
 * product's events stay ordered in its partition. A row is marked published only after the
 * broker acknowledges it; a failed send leaves the row pending for the next sweep.
 */
@Component
class OutboxRelay {

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;
    private final int batch;

    OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafkaTemplate,
                @Value("${outbox.topic:browsing-offer}") String topic,
                @Value("${outbox.batch:100}") int batch) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
        this.batch = batch;
    }

    @Scheduled(initialDelayString = "${outbox.initial-delay:PT1S}",
            fixedDelayString = "${outbox.delay:PT5S}")
    void relay() {
        relay(batch);
    }

    void relay(int limit) {
        for (OutboxMessage message : repository.pending(limit)) {
            send(message);
            repository.markPublished(message.id());
        }
    }

    private void send(OutboxMessage message) {
        try {
            kafkaTemplate.send(topic, message.partitionKey(), message.payload()).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("outbox relay interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("outbox send failed for row " + message.id(), e);
        }
    }
}
