package com.example.offer.pricing;

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
import java.util.UUID;

import static com.example.offer.pricing.PricingFixture.audit;
import static com.example.offer.pricing.PricingFixture.between;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.openFrom;
import static com.example.offer.pricing.PricingFixture.pln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class PriceScheduleDocumentWithHistoryRepositoryTest {

    @Autowired
    PriceScheduleDocumentWithHistoryRepository repository;
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
        PriceScheduleSnapshot original = fullSnapshot();

        repository.save(PriceSchedule.restore(original));
        entityManager.flush();
        entityManager.clear();

        PriceScheduleSnapshot reloaded = repository.get(original.productId()).orElseThrow().toSnapshot();

        assertThat(reloaded).isEqualTo(original);
        assertThat(reloaded.productId()).isEqualTo("p-roundtrip");
        assertThat(reloaded.prices()).containsExactly(
                new Price("pr-1", "p-roundtrip", pln("249.00"), between("2019-06-01", "2019-06-30")),
                new Price("pr-2", "p-roundtrip", pln("259.00"), openFrom("2019-07-01")));
        assertThat(reloaded.discounts()).containsExactly(
                new Discount("pr-3", "p-roundtrip", Percent.of("10"), between("2019-07-01", "2019-07-31")),
                new Discount("pr-4", "p-roundtrip", Percent.of("15"), openFrom("2019-08-01")));
        assertThat(reloaded.prices().get(1).validity().to()).isNull();
        assertThat(reloaded.discounts().get(1).validity().to()).isNull();
    }

    @Test
    void saveAppendsOneRowPerEmittedEventInEmissionOrder() {
        String productId = randomId();
        PriceSchedule schedule = PriceSchedule.forProduct(productId);
        schedule.schedulePrice("pr-2", pln("259.00"), openFrom("2019-07-01"), audit());
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());
        schedule.deleteDiscount("pr-3", date("2019-06-20"), audit());
        List<DomainEvent> emitted = List.copyOf(schedule.events);

        repository.save(schedule);
        entityManager.flush();

        assertThat(emitted).hasSize(3);
        assertThat(storedEvents(productId)).containsExactlyElementsOf(emitted);
        assertThat(published.events).containsExactlyElementsOf(emitted);
        assertThat(published.snapshots).containsExactly(schedule.toSnapshot());
    }

    @Test
    void secondSaveAppendsOnlyNewEvents() {
        String productId = randomId();
        PriceSchedule schedule = PriceSchedule.forProduct(productId);
        schedule.schedulePrice("pr-2", pln("259.00"), openFrom("2019-07-01"), audit());
        repository.save(schedule);
        entityManager.flush();

        List<DomainEvent> firstSave = storedEvents(productId);
        assertThat(firstSave).hasSize(1);

        PriceSchedule reloaded = repository.get(productId).orElseThrow();
        reloaded.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());
        repository.save(reloaded);
        entityManager.flush();

        List<DomainEvent> secondSave = storedEvents(productId);
        assertThat(secondSave).hasSize(2);
        assertThat(secondSave.get(0)).isEqualTo(firstSave.get(0));
        assertThat(secondSave.get(1)).isEqualTo(new DomainEvent.ProductPricesChanged(
                productId, date("2019-07-01"), audit()));
    }

    @Test
    void savingTwiceUpsertsSingleDocumentAndBumpsVersion() {
        String productId = randomId();
        PriceSchedule schedule = PriceSchedule.forProduct(productId);
        schedule.schedulePrice("pr-2", pln("259.00"), between("2019-07-01", "2019-07-31"), audit());
        repository.save(schedule);
        entityManager.flush();
        long firstVersion = version(productId);

        PriceSchedule reloaded = repository.get(productId).orElseThrow();
        reloaded.schedulePrice("pr-5", pln("269.00"), between("2019-08-01", "2019-09-01"), audit());
        repository.save(reloaded);
        entityManager.flush();

        assertThat(documentRows(productId)).isEqualTo(1);
        assertThat(version(productId)).isGreaterThan(firstVersion);
    }

    @Test
    void staleWriteFailsWithOptimisticLock() {
        String productId = randomId();
        PriceSchedule schedule = PriceSchedule.forProduct(productId);
        schedule.schedulePrice("pr-2", pln("259.00"), between("2019-07-01", "2019-07-31"), audit());
        repository.save(schedule);
        entityManager.flush();

        jdbc.update("update price_schedule_document set version = version + 1 where product_id = ?", productId);

        PriceSchedule stale = repository.get(productId).orElseThrow();
        stale.schedulePrice("pr-5", pln("269.00"), between("2019-08-01", "2019-09-01"), audit());
        repository.save(stale);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(OptimisticLockException.class, StaleStateException.class);
    }

    private List<DomainEvent> storedEvents(String productId) {
        return jdbc.queryForList(
                        "select event::text from price_schedule_events where product_id = ? order by sequence",
                        String.class, productId)
                .stream()
                .map(json -> JsonConfiguration.OBJECT_MAPPER.readValue(json, DomainEvent.class))
                .toList();
    }

    private long version(String productId) {
        return jdbc.queryForObject("select version from price_schedule_document where product_id = ?",
                Long.class, productId);
    }

    private int documentRows(String productId) {
        return jdbc.queryForObject("select count(*) from price_schedule_document where product_id = ?",
                Integer.class, productId);
    }

    private static String randomId() {
        return UUID.randomUUID().toString();
    }

    private static PriceScheduleSnapshot fullSnapshot() {
        return new PriceScheduleSnapshot("p-roundtrip",
                List.of(new Price("pr-1", "p-roundtrip", pln("249.00"), between("2019-06-01", "2019-06-30")),
                        new Price("pr-2", "p-roundtrip", pln("259.00"), openFrom("2019-07-01"))),
                List.of(new Discount("pr-3", "p-roundtrip", Percent.of("10"), between("2019-07-01", "2019-07-31")),
                        new Discount("pr-4", "p-roundtrip", Percent.of("15"), openFrom("2019-08-01"))));
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
        final List<PriceScheduleSnapshot> snapshots = new ArrayList<>();

        @EventListener
        void recordEvent(DomainEvent event) {
            events.add(event);
        }

        @EventListener
        void recordSnapshot(PriceScheduleSnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }
}
