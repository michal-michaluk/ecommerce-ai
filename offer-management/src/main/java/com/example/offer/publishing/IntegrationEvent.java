package com.example.offer.publishing;

import com.example.offer.auth.Audit;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The three events that cross the boundary to {@code browsing offer} (element 03, F1).
 * The other eight domain events are private and have no representation here.
 *
 * <p>The contract idempotency keys follow element 03: {@code (productId, version)} for a published
 * version, {@code (productId, effectiveFrom)} for prices and {@code (productId, removedAt)} for
 * removal. {@link #idempotencyKey()} exposes exactly that key. The outbox stores a wider
 * {@link #outboxKey()}: the contract key plus the crossing entry id, so a price and a discount
 * that become {@code ACTIVE} on the same date still emit one row each.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = IntegrationEvent.ProductVersionPublishedToOffer.class,
                name = "ProductVersionPublishedToOffer_v1"),
        @JsonSubTypes.Type(value = IntegrationEvent.ProductPricesChanged.class,
                name = "ProductPricesChanged_v1"),
        @JsonSubTypes.Type(value = IntegrationEvent.ProductRemovedFromOffer.class,
                name = "ProductRemovedFromOffer_v1")
})
public sealed interface IntegrationEvent {

    String productId();

    /** The element-03 contract idempotency key the shop dedupes on. */
    String idempotencyKey();

    /** The outbox storage key: the contract key narrowed per crossing entry (at-least-once). */
    default String outboxKey() {
        return typeName() + "|" + idempotencyKey();
    }

    default String typeName() {
        return switch (this) {
            case ProductVersionPublishedToOffer ignored -> "ProductVersionPublishedToOffer_v1";
            case ProductPricesChanged ignored -> "ProductPricesChanged_v1";
            case ProductRemovedFromOffer ignored -> "ProductRemovedFromOffer_v1";
        };
    }

    /** The shop's view of a photo, resolved because the draft is private. */
    record PhotoView(String photoId, String mime, int width, int height) {
    }

    /** Money as the contract carries it: a decimal string and an ISO-4217 code. */
    record PriceView(String value, String currency) {
    }

    record ProductVersionPublishedToOffer(String productId, String version, LocalDate availableFrom,
                                          String title, String description, Map<String, String> attributes,
                                          List<PhotoView> photos, Audit audit) implements IntegrationEvent {

        public ProductVersionPublishedToOffer {
            attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
            photos = List.copyOf(photos);
        }

        @Override
        public String idempotencyKey() {
            return productId + ":" + version;
        }
    }

    /**
     * The resolved view at {@code effectiveFrom}: the active base price and the active discount.
     * Per RULE-39 the price is {@code null} when no base price applies, regardless of discounts.
     */
    record ProductPricesChanged(String productId, LocalDate effectiveFrom, PriceView price,
                                String discountPercent, @JsonIgnore String entryId,
                                Audit audit) implements IntegrationEvent {

        @Override
        public String idempotencyKey() {
            return productId + ":" + effectiveFrom;
        }

        @Override
        public String outboxKey() {
            return typeName() + "|" + idempotencyKey() + "|" + entryId;
        }
    }

    record ProductRemovedFromOffer(String productId, Audit audit) implements IntegrationEvent {

        @Override
        public String idempotencyKey() {
            return productId + ":" + audit.at();
        }
    }
}
