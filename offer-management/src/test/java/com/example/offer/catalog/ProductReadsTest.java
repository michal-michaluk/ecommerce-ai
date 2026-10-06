package com.example.offer.catalog;

import com.example.offer.IntegrationTest;
import com.example.offer.draft.DraftState;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferState;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static com.example.offer.catalog.CatalogFixture.CATEGORY;
import static com.example.offer.catalog.CatalogFixture.activePrice;
import static com.example.offer.catalog.CatalogFixture.audit;
import static com.example.offer.catalog.CatalogFixture.decidedReview;
import static com.example.offer.catalog.CatalogFixture.draft;
import static com.example.offer.catalog.CatalogFixture.draftWithoutPrice;
import static com.example.offer.catalog.CatalogFixture.id;
import static com.example.offer.catalog.CatalogFixture.pendingReview;
import static com.example.offer.catalog.CatalogFixture.product;
import static com.example.offer.catalog.CatalogFixture.publication;
import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
@Transactional
class ProductReadsTest {

    @Autowired
    ApplicationEventPublisher publisher;
    @Autowired
    ProductReadsProjection reads;
    @Autowired
    ProductCompletenessProjection completeness;
    @Autowired
    ReviewQueueProjection reviews;
    @Autowired
    EntityManager entityManager;

    @Test
    void freshDraftIsDraft() {
        String productId = id();

        publishDraft(draft(productId, DraftState.EDITING, null));

        assertThat(state(productId)).isEqualTo(OfferState.DRAFT);
        assertThat(read(productId).category()).isEqualTo(CATEGORY);
    }

    @Test
    void visiblePublicationWinsAndExposesTheVersion() {
        String productId = id();
        publishDraft(draft(productId, DraftState.EDITING, null));

        publishProduct(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v1", LocalDate.now().minusDays(1)))));

        ProductRead read = read(productId);
        assertThat(read.state()).isEqualTo(OfferState.PUBLISHED);
        assertThat(read.visibleVersion()).isEqualTo("v1");
        assertThat(read.scheduledVersion()).isNull();
    }

    @Test
    void futurePublicationIsScheduledAndNotVisible() {
        String productId = id();
        publishDraft(draft(productId, DraftState.EDITING, null));

        publishProduct(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v2", LocalDate.now().plusDays(5)))));

        ProductRead read = read(productId);
        assertThat(read.state()).isEqualTo(OfferState.SCHEDULED);
        assertThat(read.scheduledVersion()).isEqualTo("v2");
        assertThat(read.visibleVersion()).isNull();
    }

    @Test
    void draftInReviewIsPendingReview() {
        String productId = id();

        publishDraft(draft(productId, DraftState.IN_REVIEW, pendingReview("rr-1")));

        assertThat(state(productId)).isEqualTo(OfferState.PENDING_REVIEW);
    }

    @Test
    void approvedIncompleteDraftIsBlockedBecauseThePublishGuardRefusesIt() {
        String productId = id();

        publishDraft(draftWithoutPrice(productId, DraftState.APPROVED));

        assertThat(state(productId)).isEqualTo(OfferState.BLOCKED);
        assertThat(completeness.get(productId).orElseThrow().isComplete()).isFalse();
    }

    @Test
    void approvedCompleteDraftStaysDraftUntilPublished() {
        String productId = id();
        publishDraft(draftWithoutPrice(productId, DraftState.APPROVED));

        publishPrices(activePrice(productId));

        assertThat(state(productId)).isEqualTo(OfferState.DRAFT);
        assertThat(completeness.get(productId).orElseThrow().isComplete()).isTrue();
    }

    @Test
    void removalWinsOverEveryOtherPrecedence() {
        String productId = id();
        publishDraft(draft(productId, DraftState.EDITING, null));
        publishProduct(product(productId, OfferPresence.PRESENT,
                List.of(publication(productId, "v1", LocalDate.now().minusDays(1)))));
        assertThat(state(productId)).isEqualTo(OfferState.PUBLISHED);

        publishProduct(product(productId, OfferPresence.REMOVED, List.of()));

        ProductRead read = read(productId);
        assertThat(read.state()).isEqualTo(OfferState.REMOVED);
        assertThat(read.visibleVersion()).isNull();
        assertThat(read.scheduledVersion()).isNull();
    }

    @Test
    void updatedByAndUpdatedAtFollowTheLatestChange() {
        String productId = id();
        publishDraft(draft(productId, DraftState.EDITING, null));

        Instant later = CatalogFixture.AT.plusSeconds(3600);
        publisher.publishEvent(new com.example.offer.draft.DraftSnapshot(productId, "v1",
                DraftState.EDITING, 2, new com.example.offer.draft.Title("Kosiarka"),
                new com.example.offer.draft.Description("Opis"), new com.example.offer.draft.DraftAttributes(CATEGORY, null),
                List.of(), null, null, audit("m.nowak", later)));

        ProductRead read = read(productId);
        assertThat(read.updatedBy()).isEqualTo("m.nowak");
        assertThat(read.updatedAt()).isEqualTo(later);
    }

    @Test
    void replayingASnapshotIsIdempotent() {
        String productId = id();
        var snapshot = draft(productId, DraftState.EDITING, null);

        publishDraft(snapshot);
        publishDraft(snapshot);

        assertThat(state(productId)).isEqualTo(OfferState.DRAFT);
        assertThat(read(productId).updatedBy()).isEqualTo("a.kowalska");
    }

    @Test
    void reviewQueuePreservesSubmittedAtAfterTheDecision() {
        String productId = id();
        String reviewRequestId = "rr-" + productId;

        publishDraft(draft(productId, DraftState.IN_REVIEW, pendingReview(reviewRequestId)));
        Instant decidedAt = CatalogFixture.AT.plusSeconds(7200);
        publishDraft(draft(productId, DraftState.APPROVED, decidedReview(reviewRequestId, decidedAt)));

        ReviewRequestRead review = reviews.find(reviewRequestId).orElseThrow();
        assertThat(review.status()).isEqualTo("APPROVED");
        assertThat(review.submittedAt()).isEqualTo(CatalogFixture.AT);
        assertThat(review.decidedAt()).isEqualTo(decidedAt);
        assertThat(review.decidedBy()).isEqualTo("m.nowak");
    }

    @Test
    void completenessStoresMissingInCatalogueOrder() {
        String productId = id();
        publisher.publishEvent(new com.example.offer.draft.DraftSnapshot(productId, "v1",
                DraftState.EDITING, 1, new com.example.offer.draft.Title("Kosiarka"),
                null, new com.example.offer.draft.DraftAttributes(null, null),
                List.of(), null, null, audit()));

        assertThat(completeness.get(productId).orElseThrow().getMissing())
                .extracting(com.example.offer.offer.Completeness.MissingRequirement::code)
                .containsExactly("DESCRIPTION_REQUIRED", "PHOTO_REQUIRED", "PRICE_REQUIRED");
    }

    private void publishDraft(com.example.offer.draft.DraftSnapshot snapshot) {
        publisher.publishEvent(snapshot);
    }

    private void publishProduct(com.example.offer.offer.ProductSnapshot snapshot) {
        publisher.publishEvent(snapshot);
    }

    private void publishPrices(com.example.offer.pricing.PriceScheduleSnapshot snapshot) {
        publisher.publishEvent(snapshot);
    }

    private OfferState state(String productId) {
        return read(productId).state();
    }

    private ProductRead read(String productId) {
        entityManager.flush();
        return reads.find(productId).orElseThrow();
    }
}
