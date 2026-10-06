package com.example.offer.offer;

import com.example.offer.IntegrationTest;
import com.example.offer.tools.JsonConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.example.offer.offer.OfferFixture.AT;
import static com.example.offer.offer.OfferFixture.PRODUCT_ID;
import static com.example.offer.offer.OfferFixture.REVIEWER;
import static com.example.offer.offer.OfferFixture.audit;
import static com.example.offer.offer.OfferFixture.date;
import static com.example.offer.offer.OfferFixture.givenPublishedProduct;
import static com.example.offer.offer.OfferFixture.laterAudit;
import static com.example.offer.offer.OfferFixture.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class ProductDocumentWithHistoryRepositoryTest {

    @Autowired
    ProductDocumentWithHistoryRepository repository;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;
    @Autowired
    PublishedEventsRecorder published;

    @BeforeEach
    void resetPublished() {
        published.events.clear();
        published.snapshots.clear();
    }

    @Test
    void saveThenGetRestoresEveryField() {
        Product product = givenPublishedProduct("v1", null, date("2019-06-20"));
        product.changeDraftState(DraftState.APPROVED, laterAudit());
        product.publish("pub-v2", version("v2"), date("2019-07-01"), date("2019-06-20"), List.of(), laterAudit());
        ProductSnapshot original = product.toSnapshot();

        repository.save(product);
        entityManager.flush();
        entityManager.clear();

        ProductSnapshot reloaded = repository.get(PRODUCT_ID).orElseThrow().toSnapshot();

        assertThat(reloaded).isEqualTo(original);
        assertThat(reloaded.productId()).isEqualTo(PRODUCT_ID);
        assertThat(reloaded.offerPresence()).isEqualTo(OfferPresence.PRESENT);
        assertThat(reloaded.draftState()).isNull();
        assertThat(reloaded.versions()).extracting(DescriptionVersion::version).containsExactly("v1", "v2");
        assertThat(reloaded.publications()).extracting(Publication::publicationId).containsExactly("pub-v1", "pub-v2");
    }

    @Test
    void saveAppendsOneRowPerEmittedEventInEmissionOrder() {
        String productId = randomId();
        Product product = Product.newProduct(productId, audit());
        product.changeDraftState(DraftState.APPROVED, laterAudit());
        product.publish("pub-v1", versionFor(productId, "v1"), null, date("2019-06-20"), List.of(), laterAudit());
        product.revert("v2", "v1", laterAudit());
        product.removeFromOffer(laterAudit());
        List<DomainEvent> emitted = List.copyOf(product.events);

        repository.save(product);
        entityManager.flush();

        assertThat(emitted).hasSize(3);
        assertThat(storedEvents(productId)).containsExactlyElementsOf(emitted);
        assertThat(published.events).containsExactlyElementsOf(emitted);
        assertThat(published.snapshots).containsExactly(product.toSnapshot());
    }

    @Test
    void secondSaveAppendsOnlyNewEvents() {
        String productId = randomId();
        Product product = Product.newProduct(productId, audit());
        repository.save(product);
        entityManager.flush();

        assertThat(storedEvents(productId)).isEmpty();

        Product reloaded = repository.get(productId).orElseThrow();
        reloaded.removeFromOffer(laterAudit());
        repository.save(reloaded);
        entityManager.flush();

        List<DomainEvent> stored = storedEvents(productId);
        assertThat(stored).singleElement().isEqualTo(
                new DomainEvent.ProductRemovedFromOffer(productId, laterAudit()));
    }

    @Test
    void savingTwiceUpsertsSingleDocumentAndBumpsVersion() {
        String productId = randomId();
        Product product = Product.newProduct(productId, audit());
        repository.save(product);
        entityManager.flush();
        long firstVersion = documentVersion(productId);

        Product reloaded = repository.get(productId).orElseThrow();
        reloaded.removeFromOffer(laterAudit());
        repository.save(reloaded);
        entityManager.flush();

        assertThat(documentRows(productId)).isEqualTo(1);
        assertThat(documentVersion(productId)).isGreaterThan(firstVersion);
    }

    @Test
    void staleWriteFailsWithOptimisticLock() {
        String productId = randomId();
        repository.save(Product.newProduct(productId, audit()));
        entityManager.flush();

        jdbc.update("update product_document set version = version + 1 where product_id = ?", productId);

        Product stale = repository.get(productId).orElseThrow();
        stale.removeFromOffer(laterAudit());
        repository.save(stale);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(OptimisticLockException.class, StaleStateException.class);
    }

    @Test
    void removedProductStillLoadsWithVersionsAndPublications() {                 // RULE-3
        Product product = givenPublishedProduct("v1", null, date("2019-06-20"));
        product.removeFromOffer(laterAudit());

        repository.save(product);
        entityManager.flush();
        entityManager.clear();

        ProductSnapshot reloaded = repository.get(PRODUCT_ID).orElseThrow().toSnapshot();
        assertThat(reloaded.offerPresence()).isEqualTo(OfferPresence.REMOVED);
        assertThat(reloaded.versions()).hasSize(1);
        assertThat(reloaded.publications()).hasSize(1);
    }

    private List<DomainEvent> storedEvents(String productId) {
        return jdbc.queryForList(
                        "select event::text from product_events where product_id = ? order by sequence",
                        String.class, productId)
                .stream()
                .map(json -> JsonConfiguration.OBJECT_MAPPER.readValue(json, DomainEvent.class))
                .toList();
    }

    private long documentVersion(String productId) {
        return jdbc.queryForObject("select version from product_document where product_id = ?",
                Long.class, productId);
    }

    private int documentRows(String productId) {
        return jdbc.queryForObject("select count(*) from product_document where product_id = ?",
                Integer.class, productId);
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }

    private static DescriptionVersion versionFor(String productId, String version) {
        return new DescriptionVersion(productId, version, "Kosiarka ręczna 340",
                "Solidna kosiarka ręczna do trawy i chwastów.", Map.of("category", "Ogród"),
                List.of("ph-1", "ph-2"), null, REVIEWER, AT);
    }

    @TestConfiguration
    static class PublishedEventsConfiguration {

        @Bean
        PublishedEventsRecorder publishedEventsRecorder() {
            return new PublishedEventsRecorder();
        }
    }

    static class PublishedEventsRecorder {

        final List<DomainEvent> events = new ArrayList<>();
        final List<ProductSnapshot> snapshots = new ArrayList<>();

        @EventListener
        void recordEvent(DomainEvent event) {
            events.add(event);
        }

        @EventListener
        void recordSnapshot(ProductSnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }
}
