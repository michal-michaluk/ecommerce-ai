package com.example.offer.offer;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.offer.OfferFixture.date;
import static com.example.offer.offer.OfferFixture.givenPublishedProduct;
import static com.example.offer.offer.OfferFixture.laterAudit;
import static com.example.offer.offer.OfferFixture.publication;
import static com.example.offer.offer.OfferFixture.version;
import static org.assertj.core.api.Assertions.assertThat;

class VisibleVersionTest {

    @Test
    void theGreatestAvailableFromAtOrBeforeTheInstantIsVisible() {      // RULE-32
        List<Publication> publications = List.of(
                publication("pub-v1", "v1", "2019-05-02"),
                publication("pub-v2", "v2", "2019-07-01"));

        assertThat(VisibleVersion.at(OfferPresence.PRESENT, publications, date("2019-06-15")))
                .extracting(visible -> visible.publication().version()).isEqualTo("v1");
        assertThat(VisibleVersion.at(OfferPresence.PRESENT, publications, date("2019-07-01")))
                .extracting(visible -> visible.publication().version()).isEqualTo("v2");
    }

    @Test
    void aFuturePublicationIsNotVisibleYet() {                          // RULE-31
        List<Publication> publications = List.of(publication("pub-v2", "v2", "2019-07-01"));

        assertThat(VisibleVersion.at(OfferPresence.PRESENT, publications, date("2019-06-15")).isPresent())
                .isFalse();
    }

    @Test
    void removedOfferHidesEveryVersion() {                              // RULE-40
        List<Publication> publications = List.of(publication("pub-v1", "v1", "2019-05-02"));

        assertThat(VisibleVersion.at(OfferPresence.REMOVED, publications, date("2019-06-15")).isPresent())
                .isFalse();
    }

    @Test
    void aCancelledPublicationIsNeverVisible() {                       // RULE-33
        Publication cancelled = publication("pub-v2", "v2", "2019-07-01").cancel(date("2019-06-15"));

        assertThat(VisibleVersion.at(OfferPresence.PRESENT, List.of(cancelled), date("2019-08-01")).isPresent())
                .isFalse();
    }

    @Test
    void theVisibleProjectionFollowsTheBusinessDate() {                // RULE-4, RULE-40
        Product product = givenPublishedProduct("v1", null, LocalDate.parse("2019-06-20"));
        Product scheduled = product;
        scheduled.changeDraftState(DraftState.APPROVED, laterAudit());
        scheduled.publish("pub-v2", version("v2"), date("2019-07-01"), date("2019-06-20"), List.of(), laterAudit());

        assertThat(product.toSnapshot(date("2019-06-20")).visibleVersion()).isEqualTo("v1");
        assertThat(product.toSnapshot(date("2019-07-01")).visibleVersion()).isEqualTo("v2");

        product.removeFromOffer(laterAudit());
        assertThat(product.toSnapshot(date("2019-07-01")).visibleVersion()).isNull();
    }
}
