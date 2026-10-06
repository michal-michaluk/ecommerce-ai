package com.example.offer.mediators;

import com.example.offer.IntegrationTest;
import com.example.offer.JsonAssert;
import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.Description;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.Photo;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.draft.Title;
import com.example.offer.draft.UpdateDraft;
import com.example.offer.offer.DescriptionVersion;
import com.example.offer.offer.DraftState;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.OfferService;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.pricing.DateRange;
import com.example.offer.pricing.Money;
import com.example.offer.pricing.PricingService;
import com.example.offer.tools.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@IntegrationTest
@Transactional
class OfferLifecycleMediatorTest {

    static final String PRODUCT_ID = "p-story";
    static final String MISSING_ID = "p-story-missing";
    static final Identity MANAGER = new Identity("a.kowalska");
    static final Identity REVIEWER = new Identity("m.nowak");
    static final Instant AT = Instant.parse("2019-06-19T09:00:00Z");
    static final LocalDate BUSINESS_DATE = LocalDate.parse("2019-06-20");

    @Autowired
    OfferLifecycleMediator mediator;
    @Autowired
    OfferService offers;
    @Autowired
    PricingService prices;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    EntityManager entityManager;

    @Test
    void theDomainStoryRunsEndToEnd() {                                         // E04 §2 steps 1..12
        createEditedAndPricedProduct(activePrice());
        mediator.requestReview(PRODUCT_ID, new ReviewRequest("rr-1", MANAGER, null, AT), BUSINESS_DATE, manager());
        assertThat(offers.get(PRODUCT_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::draftState).isEqualTo(DraftState.IN_REVIEW);

        mediator.approve(PRODUCT_ID, new ReviewRequest("rr-1", MANAGER, REVIEWER, AT.plusSeconds(3600)), reviewer());
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());
        mediator.publish(PRODUCT_ID, "pub-1", frozen, null, BUSINESS_DATE, reviewer());

        ProductSnapshot published = offers.get(PRODUCT_ID, BUSINESS_DATE).orElseThrow();
        assertThat(published.visibleVersion()).isEqualTo("v1");
        assertThat(published.versions()).singleElement()
                .extracting(DescriptionVersion::version).isEqualTo("v1");
        assertThat(published.publications()).singleElement()
                .extracting(version -> version.publicationId()).isEqualTo("pub-1");
        entityManager.flush();
        assertThat(rows("description_version_document")).isEqualTo(1);
        assertThat(rows("publication_document")).isEqualTo(1);

        mediator.revert(PRODUCT_ID, "v1", "v2", BUSINESS_DATE, manager());
        assertThat(offers.get(PRODUCT_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::draftState).isEqualTo(DraftState.EDITING);

        mediator.removeFromOffer(PRODUCT_ID, manager());
        assertThat(offers.get(PRODUCT_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::offerPresence).isEqualTo(OfferPresence.REMOVED);
    }

    @Test
    void revertCarriesTheBaseVersionsPhotosIntoTheNewDraft() {                  // RULE-12, E04 §5
        createEditedAndPricedProduct(activePrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());
        mediator.publish(PRODUCT_ID, "pub-1", frozen, null, BUSINESS_DATE, reviewer());

        DraftSnapshot reverted = mediator.revert(PRODUCT_ID, "v1", "v2", BUSINESS_DATE, manager());

        assertThat(reverted.version()).isEqualTo("v2");
        assertThat(reverted.state().name()).isEqualTo("EDITING");
        assertThat(reverted.photos()).extracting(Photo::photoId).containsExactly("ph-1");
    }

    @Test
    void publishGuardReadsTheCurrentPriceStateOfTheFrozenVersion() {           // D2/P2, RULE-50
        createEditedAndPricedProduct(expiredPrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());

        assertThatExceptionOfType(DecisionDenied.class)
                .isThrownBy(() -> mediator.publish(PRODUCT_ID, "pub-expired", frozen, null, BUSINESS_DATE, reviewer()))
                .satisfies(denied -> assertThat(denied.code()).isEqualTo(ErrorCode.PUBLICATION_BLOCKED));
    }

    @Test
    void publishGuardRejectsAVersionThatWasNeverFrozen() {                     // D2/P4
        createEditedAndPricedProduct(activePrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion unknown = new DescriptionVersion(PRODUCT_ID, "v9", "T", "D",
                Map.of(), List.of("ph-1"), null, REVIEWER, AT);

        assertThatExceptionOfType(DecisionDenied.class)
                .isThrownBy(() -> mediator.publish(PRODUCT_ID, "pub-unknown", unknown, null, BUSINESS_DATE, reviewer()))
                .satisfies(denied -> assertThat(denied.code()).isEqualTo(ErrorCode.VERSION_NOT_FOUND));
    }

    @Test
    void publishGuardRejectsAnAvailableFromBeforeCreation() {                  // D4/T4
        createEditedAndPricedProduct(activePrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());

        assertThatExceptionOfType(DecisionDenied.class)
                .isThrownBy(() -> mediator.publish(PRODUCT_ID, "pub-timing", frozen,
                        BUSINESS_DATE.minusDays(30), BUSINESS_DATE, reviewer()))
                .satisfies(denied -> assertThat(denied.code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void publishSchedulesAFutureVersionWithoutHidingTheCurrentOne() {           // D4/T3, RULE-55
        createEditedAndPricedProduct(activePrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());

        mediator.publish(PRODUCT_ID, "pub-future", frozen, BUSINESS_DATE.plusDays(10), BUSINESS_DATE, reviewer());

        ProductSnapshot snapshot = offers.get(PRODUCT_ID, BUSINESS_DATE).orElseThrow();
        assertThat(snapshot.visibleVersion()).isNull();
        assertThat(snapshot.scheduledVersion()).isEqualTo("v1");
    }

    @Test
    void approveRejectsTheAuthor() {                                           // D5/S2
        createEditedAndPricedProduct(activePrice());
        mediator.requestReview(PRODUCT_ID, new ReviewRequest("rr-2", MANAGER, null, AT), BUSINESS_DATE, manager());

        assertThatExceptionOfType(DecisionDenied.class)
                .isThrownBy(() -> mediator.approve(PRODUCT_ID,
                        new ReviewRequest("rr-2", MANAGER, MANAGER, AT.plusSeconds(3600)), manager()))
                .satisfies(denied -> assertThat(denied.code()).isEqualTo(ErrorCode.REVIEWER_IS_AUTHOR));
    }

    @Test
    void requestingAReviewWithMissingItemsIsAllowed() {                        // D3/V2
        mediator.createProduct(MISSING_ID, "v1", new Title("Kosiarka ręczna 340"), manager());
        mediator.editDraft(MISSING_ID, UpdateDraft.builder()
                .description(new Description("Solidna kosiarka.")).build(), manager());
        mediator.attachPhoto(MISSING_ID, photo(), manager());

        mediator.requestReview(MISSING_ID, new ReviewRequest("rr-3", MANAGER, null, AT),
                BUSINESS_DATE, manager());

        assertThat(offers.get(MISSING_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::draftState).isEqualTo(DraftState.IN_REVIEW);
    }

    @Test
    void publishAppendsTheVersionPublishedEventToTheOutboxInTheSameTransaction() {  // element 03
        createEditedAndPricedProduct(activePrice());
        reviewAndApprove(PRODUCT_ID);
        DescriptionVersion frozen = mediator.freeze(PRODUCT_ID, reviewer());

        mediator.publish(PRODUCT_ID, "pub-1", frozen, null, BUSINESS_DATE, reviewer());

        assertThat(offers.get(PRODUCT_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::visibleVersion).isEqualTo("v1");
        assertThat(outboxTypes()).containsExactly("ProductVersionPublishedToOffer_v1");
        JsonAssert.assertThat(outboxPayload("ProductVersionPublishedToOffer_v1")).isExactlyLike("""
                {"@type":"ProductVersionPublishedToOffer_v1","productId":"p-story","version":"v1",
                 "availableFrom":null,"title":"Kosiarka r\u0119czna 340",
                 "description":"Solidna kosiarka r\u0119czna do trawy i chwast\u00f3w.",
                 "attributes":{},
                 "photos":[{"photoId":"ph-1","mime":"image/jpeg","width":1200,"height":1200}],
                 "audit":{"who":{"subject":"m.nowak"},"at":"2019-06-19T10:00:00Z"}}
                """);
    }

    @Test
    void removeFromOfferAppendsTheRemovedEventToTheOutboxInTheSameTransaction() {   // element 03
        mediator.createProduct(PRODUCT_ID, "v1", new Title("Kosiarka r\u0119czna 340"), manager());

        mediator.removeFromOffer(PRODUCT_ID, manager());

        assertThat(offers.get(PRODUCT_ID, BUSINESS_DATE)).get()
                .extracting(ProductSnapshot::offerPresence).isEqualTo(OfferPresence.REMOVED);
        assertThat(outboxTypes()).containsExactly("ProductRemovedFromOffer_v1");
        JsonAssert.assertThat(outboxPayload("ProductRemovedFromOffer_v1")).isExactlyLike("""
                {"@type":"ProductRemovedFromOffer_v1","productId":"p-story",
                 "audit":{"who":{"subject":"a.kowalska"},"at":"2019-06-19T09:00:00Z"}}
                """);
    }

    private void createEditedAndPricedProduct(DateRange validity) {
        mediator.createProduct(PRODUCT_ID, "v1", new Title("Kosiarka ręczna 340"), manager());
        mediator.editDraft(PRODUCT_ID, UpdateDraft.builder()
                .description(new Description("Solidna kosiarka ręczna do trawy i chwastów.")).build(), manager());
        mediator.attachPhoto(PRODUCT_ID, photo(), manager());
        prices.schedulePrice(PRODUCT_ID, "pr-1", Money.of("259.00", "PLN"), validity, manager());
    }

    private void reviewAndApprove(String productId) {
        mediator.requestReview(productId, new ReviewRequest("rr-1", MANAGER, null, AT), BUSINESS_DATE, manager());
        mediator.approve(productId, new ReviewRequest("rr-1", MANAGER, REVIEWER, AT.plusSeconds(3600)), reviewer());
    }

    private int rows(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where product_id = ?", Integer.class, PRODUCT_ID);
    }

    private List<String> outboxTypes() {
        return jdbc.queryForList("select event_type from outbox where partition_key = ? order by id",
                String.class, PRODUCT_ID);
    }

    private String outboxPayload(String eventType) {
        return jdbc.queryForObject("select payload::text from outbox where partition_key = ? and event_type = ?",
                String.class, PRODUCT_ID, eventType);
    }

    private static DateRange activePrice() {
        return DateRange.from(BUSINESS_DATE);
    }

    private static DateRange expiredPrice() {
        return new DateRange(BUSINESS_DATE.minusDays(30), BUSINESS_DATE.minusDays(10));
    }

    private static Audit manager() {
        return new Audit(MANAGER, AT);
    }

    private static Audit reviewer() {
        return new Audit(REVIEWER, AT.plusSeconds(3600));
    }

    private static Photo photo() {
        return new Photo("ph-1", "kosiarka-01.jpg", "image/jpeg", 1200, 1200, 184320, 0, AT);
    }
}
