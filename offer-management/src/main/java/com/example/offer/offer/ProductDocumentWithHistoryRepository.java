package com.example.offer.offer;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
@AllArgsConstructor
class ProductDocumentWithHistoryRepository implements ProductRepository {

    private final DocumentRepository documents;
    private final EventRepository events;
    private final ApplicationEventPublisher publisher;

    @Override
    public Optional<Product> get(String productId) {
        return documents.findById(productId)
                .map(ProductDocumentEntity::getProduct)
                .map(snapshot -> new Product(snapshot.productId(), new ArrayList<>(),
                        snapshot.offerPresence(), snapshot.draftState(), snapshot.versions(),
                        snapshot.publications(), snapshot.createdAt(), snapshot.lastChange()));
    }

    @Override
    public void save(Product product) {
        ProductSnapshot snapshot = product.toSnapshot();
        List<DomainEvent> emitted = drain(product);
        documents.save(documents.findById(snapshot.productId())
                .orElseGet(() -> new ProductDocumentEntity(snapshot.productId()))
                .setProduct(snapshot));
        emitted.forEach(event -> events.save(new ProductEventEntity(snapshot.productId(), event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(snapshot);
        }
        emitted.forEach(publisher::publishEvent);
    }

    private static List<DomainEvent> drain(Product product) {
        List<DomainEvent> emitted = List.copyOf(product.events);
        product.events.clear();
        return emitted;
    }

    @Repository
    interface DocumentRepository extends CrudRepository<ProductDocumentEntity, String> {
    }

    @Repository
    interface EventRepository extends CrudRepository<ProductEventEntity, Long> {
    }

    @Entity
    @Table(name = "product_document")
    @NoArgsConstructor
    static class ProductDocumentEntity {

        @Id
        private String productId;
        @Version
        private long version;
        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private ProductSnapshot product;

        ProductDocumentEntity(String productId) {
            this.productId = productId;
        }

        ProductDocumentEntity setProduct(ProductSnapshot product) {
            this.product = product;
            return this;
        }
    }

    @Entity
    @Table(name = "product_events")
    @NoArgsConstructor
    static class ProductEventEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long sequence;
        private String productId;
        @JdbcTypeCode(SqlTypes.JSON)
        private DomainEvent event;

        ProductEventEntity(String productId, DomainEvent event) {
            this.productId = productId;
            this.event = event;
        }
    }
}
