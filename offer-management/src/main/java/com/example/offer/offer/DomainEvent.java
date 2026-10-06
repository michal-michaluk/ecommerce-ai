package com.example.offer.offer;

import com.example.offer.auth.Audit;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;

/**
 * The three events this context publishes across its boundary (element 03, F1). Every other
 * transition stays private.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.ProductVersionPublishedToOffer.class, name = "ProductVersionPublishedToOffer_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReverted.class,            name = "DescriptionReverted_v1"),
        @JsonSubTypes.Type(value = DomainEvent.ProductRemovedFromOffer.class,        name = "ProductRemovedFromOffer_v1")
})
public interface DomainEvent {

    /** A version was published, effective from {@code availableFrom} ({@code null} means now). */
    record ProductVersionPublishedToOffer(String productId, String version,
                                          LocalDate availableFrom, Audit audit) implements DomainEvent {
    }

    /** A revert opened a new draft based on an older version — history is never rewritten (RULE-12). */
    record DescriptionReverted(String productId, String newVersion,
                               String basedOnVersion, Audit audit) implements DomainEvent {
    }

    record ProductRemovedFromOffer(String productId, Audit audit) implements DomainEvent {
    }
}
