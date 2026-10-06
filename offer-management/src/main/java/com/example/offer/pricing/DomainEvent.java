package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.ProductPricesChanged.class, name = "ProductPricesChanged_v1")
})
public interface DomainEvent {

    /** The price schedule changed, effective from the date the change takes effect (RULE-28). */
    record ProductPricesChanged(String productId, LocalDate effectiveFrom, Audit audit) implements DomainEvent {
    }
}
