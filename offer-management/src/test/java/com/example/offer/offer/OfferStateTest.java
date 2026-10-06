package com.example.offer.offer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.offer.OfferFixture.date;
import static com.example.offer.offer.OfferFixture.givenApprovedProduct;
import static com.example.offer.offer.OfferFixture.givenProduct;
import static com.example.offer.offer.OfferFixture.givenPublishedProduct;
import static com.example.offer.offer.OfferFixture.laterAudit;
import static com.example.offer.offer.OfferFixture.version;
import static org.assertj.core.api.Assertions.assertThat;

class OfferStateTest {

    private final LocalDate businessDate = date("2019-06-20");
    private final List<String> missingPrice = List.of("price");

    @Test
    void aFreshDraftIsDraft() {                                   // RULE-34, RULE-35
        Product product = givenProduct();

        assertThat(product.offerState(businessDate, List.of())).isEqualTo(OfferState.DRAFT);
    }

    @Test
    void aDraftInReviewIsPendingReview() {                        // RULE-35
        Product product = givenProduct();
        product.changeDraftState(DraftState.IN_REVIEW, laterAudit());

        assertThat(product.offerState(businessDate, List.of())).isEqualTo(OfferState.PENDING_REVIEW);
    }

    @Test
    void anApprovedDraftMissingRequirementsIsBlocked() {          // RULE-70
        Product product = givenApprovedProduct();

        assertThat(product.offerState(businessDate, missingPrice)).isEqualTo(OfferState.BLOCKED);
    }

    @Test
    void anApprovedDraftWithNothingMissingFallsThroughToDraft() { // RULE-35, RULE-70
        Product product = givenApprovedProduct();

        assertThat(product.offerState(businessDate, List.of())).isEqualTo(OfferState.DRAFT);
    }

    @Test
    void anIncompleteEditingDraftStaysDraft() {                   // RULE-70
        Product product = givenProduct();

        assertThat(product.offerState(businessDate, missingPrice)).isEqualTo(OfferState.DRAFT);
    }

    @Test
    void onlyAFutureVersionIsScheduled() {                        // RULE-35
        Product product = givenApprovedProduct();
        product.publish("pub-v1", version("v1"), date("2019-07-01"), businessDate, List.of(), laterAudit());

        assertThat(product.offerState(businessDate, missingPrice)).isEqualTo(OfferState.SCHEDULED);
    }

    @Test
    void aVisibleVersionIsPublished() {                           // RULE-35
        Product product = givenPublishedProduct("v1", null, businessDate);

        assertThat(product.offerState(businessDate, missingPrice)).isEqualTo(OfferState.PUBLISHED);
    }

    @Test
    void removalWinsOverEveryOtherState() {                       // RULE-35, RULE-40
        Product product = givenPublishedProduct("v1", null, businessDate);

        product.removeFromOffer(laterAudit());

        assertThat(product.offerState(businessDate, missingPrice)).isEqualTo(OfferState.REMOVED);
    }
}
