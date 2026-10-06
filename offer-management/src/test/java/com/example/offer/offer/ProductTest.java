package com.example.offer.offer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.offer.OfferFixture.PRODUCT_ID;
import static com.example.offer.offer.OfferFixture.audit;
import static com.example.offer.offer.OfferFixture.date;
import static com.example.offer.offer.OfferFixture.givenApprovedProduct;
import static com.example.offer.offer.OfferFixture.givenProduct;
import static com.example.offer.offer.OfferFixture.givenPublishedProduct;
import static com.example.offer.offer.OfferFixture.laterAudit;
import static com.example.offer.offer.OfferFixture.version;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class ProductTest {

    private final LocalDate businessDate = date("2019-06-20");

    @Test
    void creatingAProductStartsExactlyOneEditableDraftAndPresentPresence() {   // RULE-1, RULE-2
        Product product = givenProduct();

        ProductSnapshot snapshot = product.toSnapshot(businessDate);
        assertThat(snapshot.draftState()).isEqualTo(DraftState.EDITING);
        assertThat(snapshot.offerPresence()).isEqualTo(OfferPresence.PRESENT);
        assertThat(snapshot.versions()).isEmpty();
        assertThat(snapshot.publications()).isEmpty();
    }

    @Test
    void removingFromOfferIsIdempotent() {                                    // RULE-2
        Product product = givenProduct();

        product.removeFromOffer(audit());
        var first = product.toSnapshot(businessDate).lastChange();
        product.removeFromOffer(laterAudit());

        assertThat(product.toSnapshot(businessDate).offerPresence()).isEqualTo(OfferPresence.REMOVED);
        assertThat(product.events).singleElement().isInstanceOf(DomainEvent.ProductRemovedFromOffer.class);
        assertThat(product.toSnapshot(businessDate).lastChange()).isEqualTo(first);
    }

    @Test
    void removingFromOfferKeepsVersionsAndPublications() {                    // RULE-3
        Product product = givenPublishedProduct("v1", null, businessDate);

        product.removeFromOffer(laterAudit());

        ProductSnapshot snapshot = product.toSnapshot(businessDate);
        assertThat(snapshot.versions()).hasSize(1);
        assertThat(snapshot.publications()).hasSize(1);
    }

    @Test
    void visibleAndScheduledVersionsAreProjectionsOfPublications() {          // RULE-4, RULE-31
        Product product = givenPublishedProduct("v1", null, businessDate);
        product.changeDraftState(DraftState.APPROVED, laterAudit());
        product.publish("pub-v2", version("v2"), date("2019-07-01"), businessDate, List.of(), laterAudit());

        ProductSnapshot snapshot = product.toSnapshot(businessDate);
        assertThat(snapshot.visibleVersion()).isEqualTo("v1");
        assertThat(snapshot.scheduledVersion()).isEqualTo("v2");
    }

    @Test
    void publishingRequiresAnApprovedVersion() {                              // RULE-10, D2/P3
        Product product = givenProduct();

        assertThatExceptionOfType(VersionNotApproved.class).isThrownBy(() ->
                product.publish("pub-v1", version("v1"), null, businessDate, List.of(), audit()));
    }

    @Test
    void publishingIsBlockedWhenRequirementsAreMissing() {                    // RULE-30, D2/P2
        Product product = givenApprovedProduct();

        assertThatExceptionOfType(PublicationBlocked.class).isThrownBy(() ->
                product.publish("pub-v1", version("v1"), null, businessDate, List.of("price"), audit()));
    }

    @Test
    void publishingPinsExactlyOneVersionAndEmitsTheBoundaryEvent() {         // RULE-29
        Product product = givenApprovedProduct();

        product.publish("pub-v1", version("v1"), null, businessDate, List.of(), laterAudit());

        assertThat(product.toSnapshot(businessDate).publications()).singleElement()
                .extracting(Publication::version).isEqualTo("v1");
        assertThat(product.events).singleElement().isEqualTo(
                new DomainEvent.ProductVersionPublishedToOffer(PRODUCT_ID, "v1", null, laterAudit()));
    }

    @Test
    void versionsAreNumberedStrictlyIncreasingAndNeverReused() {              // RULE-11
        Product product = givenPublishedProduct("v1", null, businessDate);
        product.changeDraftState(DraftState.APPROVED, laterAudit());

        assertThatExceptionOfType(VersionAlreadyExists.class).isThrownBy(() ->
                product.publish("pub-v1-again", version("v1"), null, businessDate, List.of(), laterAudit()));
    }

    @Test
    void aLaterVersionCanBeBasedOnAnOlderOne() {                             // RULE-12
        Product product = givenPublishedProduct("v1", null, businessDate);
        product.changeDraftState(DraftState.APPROVED, laterAudit());

        product.publish("pub-v2", version("v2", "v1"), null, businessDate, List.of(), laterAudit());

        assertThat(product.toSnapshot(businessDate).versions())
                .extracting(DescriptionVersion::basedOnVersion).containsExactly(null, "v1");
    }

    @Test
    void publishingRejectsAnUnknownBasisVersion() {                           // RULE-12, RULE-13
        Product product = givenApprovedProduct();

        assertThatExceptionOfType(VersionNotFound.class).isThrownBy(() ->
                product.publish("pub-v1", version("v1", "v9"), null, businessDate, List.of(), laterAudit()));
    }

    @Test
    void revertOpensANewDraftBasedOnAnOlderVersion() {                        // RULE-12
        Product product = givenPublishedProduct("v1", null, businessDate);

        product.revert("v2", "v1", laterAudit());

        assertThat(product.toSnapshot(businessDate).draftState()).isEqualTo(DraftState.EDITING);
        assertThat(product.events).last().isEqualTo(
                new DomainEvent.DescriptionReverted(PRODUCT_ID, "v2", "v1", laterAudit()));
    }

    @Test
    void revertRejectsAnUnknownBasisVersion() {                               // RULE-12
        Product product = givenProduct();

        assertThatExceptionOfType(VersionNotFound.class).isThrownBy(() ->
                product.revert("v2", "v9", laterAudit()));
    }

    @Test
    void cancellingAScheduledPublicationIsANewStateNotADelete() {            // RULE-33
        Product product = givenPublishedProduct("v1", null, businessDate);
        product.changeDraftState(DraftState.APPROVED, laterAudit());
        product.publish("pub-v2", version("v2"), date("2019-07-01"), businessDate, List.of(), laterAudit());

        product.cancelPublication("pub-v2", businessDate, laterAudit());

        assertThat(product.toSnapshot(businessDate).publications())
                .extracting(publication -> publication.stateAt(businessDate))
                .containsExactly(PublicationState.PUBLISHED, PublicationState.CANCELLED);
    }

    @Test
    void cancellingAPublishedPublicationIsRejected() {                        // RULE-33, A6
        Product product = givenPublishedProduct("v1", null, businessDate);

        assertThatExceptionOfType(PublicationNotCancellable.class).isThrownBy(() ->
                product.cancelPublication("pub-v1", businessDate, laterAudit()));
    }
}
