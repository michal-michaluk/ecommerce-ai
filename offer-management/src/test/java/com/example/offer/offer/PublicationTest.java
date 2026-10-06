package com.example.offer.offer;

import org.junit.jupiter.api.Test;

import static com.example.offer.offer.OfferFixture.PRODUCT_ID;
import static com.example.offer.offer.OfferFixture.date;
import static com.example.offer.offer.OfferFixture.publication;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PublicationTest {

    @Test
    void aPublicationPinsExactlyOneVersion() {                        // RULE-29
        Publication publication = publication("pub-v1", "v1", "2019-05-02");

        assertThat(publication.productId()).isEqualTo(PRODUCT_ID);
        assertThat(publication.version()).isEqualTo("v1");
    }

    @Test
    void aFuturePublicationIsScheduledAndNotYetExposed() {            // RULE-31, D4/T3
        Publication publication = publication("pub-v2", "v2", "2019-07-01");

        assertThat(publication.stateAt(date("2019-06-15"))).isEqualTo(PublicationState.SCHEDULED);
        assertThat(publication.exposesAt(date("2019-06-15"))).isFalse();
    }

    @Test
    void anImmediatePublicationIsPublishedAndExposed() {             // D4/T1
        Publication publication = publication("pub-v1", "v1", null);

        assertThat(publication.stateAt(date("2019-06-15"))).isEqualTo(PublicationState.PUBLISHED);
        assertThat(publication.exposesAt(date("2019-06-15"))).isTrue();
    }

    @Test
    void cancellingAScheduledPublicationIsANewStateNotADelete() {    // RULE-33, A6
        Publication scheduled = publication("pub-v2", "v2", "2019-07-01");

        Publication cancelled = scheduled.cancel(date("2019-06-15"));

        assertThat(cancelled.stateAt(date("2019-08-01"))).isEqualTo(PublicationState.CANCELLED);
        assertThat(cancelled.version()).isEqualTo("v2");
        assertThat(cancelled.exposesAt(date("2019-08-01"))).isFalse();
    }

    @Test
    void cancellingAPublishedPublicationIsRejected() {               // RULE-33, A6
        Publication published = publication("pub-v1", "v1", null);

        assertThatExceptionOfType(PublicationNotCancellable.class).isThrownBy(() ->
                published.cancel(date("2019-06-15")));
    }
}
