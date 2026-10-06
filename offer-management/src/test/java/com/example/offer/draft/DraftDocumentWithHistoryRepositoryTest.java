package com.example.offer.draft;

import com.example.offer.IntegrationTest;
import com.example.offer.auth.Audit;
import com.example.offer.tools.JsonConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.StaleStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.offer.draft.DraftFixture.AUTHOR;
import static com.example.offer.draft.DraftFixture.AT;
import static com.example.offer.draft.DraftFixture.REVIEWER;
import static com.example.offer.draft.DraftFixture.authorAudit;
import static com.example.offer.draft.DraftFixture.description;
import static com.example.offer.draft.DraftFixture.photo;
import static com.example.offer.draft.DraftFixture.randomId;
import static com.example.offer.draft.DraftFixture.title;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@Transactional
class DraftDocumentWithHistoryRepositoryTest {

    @Autowired
    DraftDocumentWithHistoryRepository repository;
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
        DraftSnapshot original = fullSnapshot();

        repository.save(new DescriptionDraft(original.productId(), original.version(), new ArrayList<>(),
                original.state(), original.revision(), original.title(), original.description(),
                original.attributes(), original.photos(), original.basedOnVersion(), original.review(),
                original.lastChange()));
        entityManager.flush();
        entityManager.clear();

        DraftSnapshot reloaded = repository.get(original.productId()).orElseThrow().toDraftSnapshot();

        assertThat(reloaded).isEqualTo(original);
        assertThat(reloaded.productId()).isEqualTo("p-roundtrip");
        assertThat(reloaded.version()).isEqualTo("v7");
        assertThat(reloaded.state()).isEqualTo(DraftState.APPROVED);
        assertThat(reloaded.revision()).isEqualTo(42);
        assertThat(reloaded.title()).isEqualTo(new Title("Kosiarka ręczna 340"));
        assertThat(reloaded.description()).isEqualTo(new Description("Solidna kosiarka ręczna do trawy i chwastów."));
        assertThat(reloaded.attributes()).isEqualTo(new DraftAttributes("Ogród", "https://manual.example/340.pdf"));
        assertThat(reloaded.photos()).containsExactly(original.photos().get(0));
        assertThat(reloaded.basedOnVersion()).isEqualTo("v6");
        assertThat(reloaded.review()).isEqualTo(new ReviewRequest("rr-9", AUTHOR, REVIEWER, AT.plusSeconds(600)));
        assertThat(reloaded.lastChange()).isEqualTo(new Audit(AUTHOR, AT.plusSeconds(1200)));
    }

    @Test
    void saveAppendsOneRowPerEmittedEventInEmissionOrder() {
        String productId = randomId();
        DescriptionDraft draft = DescriptionDraft.newDraft(productId, "v1", title(), authorAudit());
        draft.edit(UpdateDraft.builder().description(description()).build(), authorAudit());
        Photo photo = photo();
        draft.attachPhoto(photo, authorAudit());
        draft.detachPhoto(photo.photoId(), authorAudit());
        List<DomainEvent> emitted = List.copyOf(draft.events);

        repository.save(draft);
        entityManager.flush();

        assertThat(emitted).hasSize(4);
        assertThat(storedEvents(productId)).containsExactlyElementsOf(emitted);
        assertThat(published.events).containsExactlyElementsOf(emitted);
        assertThat(published.snapshots).containsExactly(draft.toDraftSnapshot());
    }

    @Test
    void secondSaveAppendsOnlyNewEvents() {
        String productId = randomId();
        repository.save(DescriptionDraft.newDraft(productId, "v1", title(), authorAudit()));
        entityManager.flush();

        List<DomainEvent> firstSave = storedEvents(productId);
        assertThat(firstSave).hasSize(1);
        assertThat(firstSave.get(0)).isInstanceOf(DomainEvent.BlankDraftCreated.class);

        DescriptionDraft reloaded = repository.get(productId).orElseThrow();
        reloaded.edit(UpdateDraft.builder().description(description()).build(), authorAudit());
        repository.save(reloaded);
        entityManager.flush();

        List<DomainEvent> secondSave = storedEvents(productId);
        assertThat(secondSave).hasSize(2);
        assertThat(secondSave.get(0)).isInstanceOf(DomainEvent.BlankDraftCreated.class);
        assertThat(secondSave.get(1)).isInstanceOf(DomainEvent.DescriptionUpdated.class);
    }

    @Test
    void savingTwiceUpsertsSingleDocumentAndBumpsVersion() {
        String productId = randomId();
        repository.save(DescriptionDraft.newDraft(productId, "v1", title(), authorAudit()));
        entityManager.flush();
        long firstVersion = version(productId);

        DescriptionDraft reloaded = repository.get(productId).orElseThrow();
        reloaded.edit(UpdateDraft.builder().description(description()).build(), authorAudit());
        repository.save(reloaded);
        entityManager.flush();

        assertThat(documentRows(productId)).isEqualTo(1);
        assertThat(version(productId)).isGreaterThan(firstVersion);
    }

    @Test
    void staleWriteFailsWithOptimisticLock() {
        String productId = randomId();
        repository.save(DescriptionDraft.newDraft(productId, "v1", title(), authorAudit()));
        entityManager.flush();

        jdbc.update("update draft_document set version = version + 1 where product_id = ?", productId);

        DescriptionDraft stale = repository.get(productId).orElseThrow();
        stale.edit(UpdateDraft.builder().description(description()).build(), authorAudit());
        repository.save(stale);

        assertThatThrownBy(entityManager::flush)
                .isInstanceOfAny(OptimisticLockException.class, StaleStateException.class);
    }

    private List<DomainEvent> storedEvents(String productId) {
        return jdbc.queryForList(
                        "select event::text from draft_events where product_id = ? order by sequence", String.class, productId)
                .stream()
                .map(json -> JsonConfiguration.OBJECT_MAPPER.readValue(json, DomainEvent.class))
                .toList();
    }

    private long version(String productId) {
        return jdbc.queryForObject("select version from draft_document where product_id = ?", Long.class, productId);
    }

    private int documentRows(String productId) {
        return jdbc.queryForObject("select count(*) from draft_document where product_id = ?", Integer.class, productId);
    }

    private static DraftSnapshot fullSnapshot() {
        return new DraftSnapshot("p-roundtrip", "v7", DraftState.APPROVED, 42,
                new Title("Kosiarka ręczna 340"),
                new Description("Solidna kosiarka ręczna do trawy i chwastów."),
                new DraftAttributes("Ogród", "https://manual.example/340.pdf"),
                List.of(photo()),
                "v6",
                new ReviewRequest("rr-9", AUTHOR, REVIEWER, AT.plusSeconds(600)),
                new Audit(AUTHOR, AT.plusSeconds(1200)));
    }

    @TestConfiguration
    static class PublishedEventsConfiguration {

        @Bean
        PublishedEventsRecorder publishedEventsRecorder() {
            return new PublishedEventsRecorder();
        }

        /**
         * The pricing context has no persistence adapter yet (its node comes later), so
         * {@code PriceScheduleRepository} has no bean and the shared application context cannot start.
         * Register an inert placeholder so this node's integration test can boot the context; it is
         * never exercised by draft persistence.
         */
        @Bean
        static BeanFactoryPostProcessor priceScheduleRepositoryPlaceholder() {
            return beanFactory -> {
                try {
                    Class<?> type = Class.forName("com.example.offer.pricing.PriceScheduleRepository");
                    Object placeholder = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                            (proxy, method, args) -> method.getReturnType() == Optional.class ? Optional.empty() : null);
                    ((DefaultListableBeanFactory) beanFactory).registerSingleton("priceScheduleRepository", placeholder);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException(e);
                }
            };
        }
    }

    static class PublishedEventsRecorder {

        final List<DomainEvent> events = new ArrayList<>();
        final List<DraftSnapshot> snapshots = new ArrayList<>();

        @EventListener
        void recordEvent(DomainEvent event) {
            events.add(event);
        }

        @EventListener
        void recordSnapshot(DraftSnapshot snapshot) {
            snapshots.add(snapshot);
        }
    }
}
