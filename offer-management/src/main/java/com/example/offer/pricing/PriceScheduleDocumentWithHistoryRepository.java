package com.example.offer.pricing;

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
class PriceScheduleDocumentWithHistoryRepository implements PriceScheduleRepository {

    private final DocumentRepository documents;
    private final EventRepository events;
    private final ApplicationEventPublisher publisher;

    @Override
    public Optional<PriceSchedule> get(String productId) {
        return documents.findById(productId)
                .map(PriceScheduleDocumentEntity::getPriceSchedule)
                .map(snapshot -> new PriceSchedule(snapshot.productId(), new ArrayList<>(),
                        snapshot.prices(), snapshot.discounts()));
    }

    @Override
    public void save(PriceSchedule schedule) {
        PriceScheduleSnapshot snapshot = schedule.toSnapshot();
        List<DomainEvent> emitted = drain(schedule);
        documents.save(documents.findById(snapshot.productId())
                .orElseGet(() -> new PriceScheduleDocumentEntity(snapshot.productId()))
                .setPriceSchedule(snapshot));
        emitted.forEach(event -> events.save(new PriceScheduleEventEntity(snapshot.productId(), event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(snapshot);
        }
        emitted.forEach(publisher::publishEvent);
    }

    private static List<DomainEvent> drain(PriceSchedule schedule) {
        List<DomainEvent> emitted = List.copyOf(schedule.events);
        schedule.events.clear();
        return emitted;
    }

    @Repository
    interface DocumentRepository extends CrudRepository<PriceScheduleDocumentEntity, String> {
    }

    @Repository
    interface EventRepository extends CrudRepository<PriceScheduleEventEntity, Long> {
    }

    @Entity
    @Table(name = "price_schedule_document")
    @NoArgsConstructor
    static class PriceScheduleDocumentEntity {

        @Id
        private String productId;
        @Version
        private long version;
        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private PriceScheduleSnapshot priceSchedule;

        PriceScheduleDocumentEntity(String productId) {
            this.productId = productId;
        }

        PriceScheduleDocumentEntity setPriceSchedule(PriceScheduleSnapshot priceSchedule) {
            this.priceSchedule = priceSchedule;
            return this;
        }
    }

    @Entity
    @Table(name = "price_schedule_events")
    @NoArgsConstructor
    static class PriceScheduleEventEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long sequence;
        private String productId;
        @JdbcTypeCode(SqlTypes.JSON)
        private DomainEvent event;

        PriceScheduleEventEntity(String productId, DomainEvent event) {
            this.productId = productId;
            this.event = event;
        }
    }
}
