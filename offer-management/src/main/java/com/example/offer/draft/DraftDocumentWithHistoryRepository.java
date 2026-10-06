package com.example.offer.draft;

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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
@AllArgsConstructor
class DraftDocumentWithHistoryRepository implements DraftRepository {

    private final DocumentRepository documents;
    private final EventRepository events;
    private final ApplicationEventPublisher publisher;

    @Override
    public Optional<DescriptionDraft> get(String productId) {
        return documents.findById(productId)
                .map(DraftDocumentEntity::getDraft)
                .map(snapshot -> new DescriptionDraft(snapshot.productId(), snapshot.version(),
                        new ArrayList<>(), snapshot.state(), snapshot.revision(), snapshot.title(),
                        snapshot.description(), snapshot.attributes(), snapshot.photos(),
                        snapshot.basedOnVersion(), snapshot.review(), snapshot.lastChange()));
    }

    @Override
    public Optional<String> productIdOfReview(String reviewRequestId) {
        return documents.findProductIdByReviewRequestId(reviewRequestId);
    }

    @Override
    public void save(DescriptionDraft draft) {
        DraftSnapshot snapshot = draft.toDraftSnapshot();
        List<DomainEvent> emitted = drain(draft);
        documents.save(documents.findById(snapshot.productId())
                .orElseGet(() -> new DraftDocumentEntity(snapshot.productId()))
                .setDraft(snapshot));
        emitted.forEach(event -> events.save(new DraftEventEntity(snapshot.productId(), event)));
        if (!emitted.isEmpty()) {
            publisher.publishEvent(snapshot);
        }
        emitted.forEach(publisher::publishEvent);
    }

    private static List<DomainEvent> drain(DescriptionDraft draft) {
        List<DomainEvent> emitted = List.copyOf(draft.events);
        draft.events.clear();
        return emitted;
    }

    @Repository
    interface DocumentRepository extends CrudRepository<DraftDocumentEntity, String> {

        @Query(value = "select product_id from draft_document "
                + "where draft -> 'review' ->> 'reviewRequestId' = :reviewRequestId", nativeQuery = true)
        Optional<String> findProductIdByReviewRequestId(@Param("reviewRequestId") String reviewRequestId);
    }

    @Repository
    interface EventRepository extends CrudRepository<DraftEventEntity, Long> {
    }

    @Entity
    @Table(name = "draft_document")
    @NoArgsConstructor
    static class DraftDocumentEntity {

        @Id
        private String productId;
        @Version
        private long version;
        @Getter
        @JdbcTypeCode(SqlTypes.JSON)
        private DraftSnapshot draft;

        DraftDocumentEntity(String productId) {
            this.productId = productId;
        }

        DraftDocumentEntity setDraft(DraftSnapshot draft) {
            this.draft = draft;
            return this;
        }
    }

    @Entity
    @Table(name = "draft_events")
    @NoArgsConstructor
    static class DraftEventEntity {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long sequence;
        private String productId;
        @JdbcTypeCode(SqlTypes.JSON)
        private DomainEvent event;

        DraftEventEntity(String productId, DomainEvent event) {
            this.productId = productId;
            this.event = event;
        }
    }
}
