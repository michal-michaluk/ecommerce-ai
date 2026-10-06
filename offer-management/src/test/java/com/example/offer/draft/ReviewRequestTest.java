package com.example.offer.draft;

import org.junit.jupiter.api.Test;

import static com.example.offer.draft.DraftFixture.AT;
import static com.example.offer.draft.DraftFixture.AUTHOR;
import static com.example.offer.draft.DraftFixture.REVIEWER;
import static com.example.offer.draft.DraftFixture.authorAudit;
import static org.assertj.core.api.Assertions.assertThat;

class ReviewRequestTest {

    @Test
    void requestedCarriesAuthorAndIsPending() {
        ReviewRequest request = ReviewRequest.requested("rr-1", authorAudit());

        assertThat(request.reviewRequestId()).isEqualTo("rr-1");
        assertThat(request.author()).isEqualTo(AUTHOR);
        assertThat(request.decidedBy()).isNull();
        assertThat(request.at()).isEqualTo(AT);
        assertThat(request.isPending()).isTrue();
        assertThat(request.isDecided()).isFalse();
    }

    @Test
    void decidedKeepsAuthorAndRecordsDecider() {
        ReviewRequest decision = ReviewRequest.decided(
                ReviewRequest.requested("rr-1", authorAudit()), REVIEWER, AT);

        assertThat(decision.reviewRequestId()).isEqualTo("rr-1");
        assertThat(decision.author()).isEqualTo(AUTHOR);
        assertThat(decision.decidedBy()).isEqualTo(REVIEWER);
        assertThat(decision.isPending()).isFalse();
        assertThat(decision.isDecided()).isTrue();
    }
}
