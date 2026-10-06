package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;

import java.time.Instant;
import java.util.UUID;

final class DraftFixture {

    static final String PRODUCT_ID = "p-2019-0442";
    static final String VERSION = "v1";
    static final Instant AT = Instant.parse("2019-10-01T09:00:00Z");

    static final Identity AUTHOR = new Identity("a.kowalska");
    static final Identity REVIEWER = new Identity("m.nowak");

    private DraftFixture() {
    }

    static String randomId() {
        return UUID.randomUUID().toString();
    }

    static Audit authorAudit() {
        return new Audit(AUTHOR, AT);
    }

    static Audit reviewerAudit() {
        return new Audit(REVIEWER, AT.plusSeconds(3600));
    }

    static Title title() {
        return new Title("Kosiarka ręczna 340");
    }

    static Description description() {
        return new Description("Solidna kosiarka ręczna do trawy i chwastów.");
    }

    static DescriptionDraft givenDraft() {
        return DescriptionDraft.newDraft(PRODUCT_ID, VERSION, title(), authorAudit());
    }

    static ReviewRequest pendingRequest(String reviewRequestId) {
        return ReviewRequest.requested(reviewRequestId, authorAudit());
    }

    static DescriptionDraft givenDraftInReview(String reviewRequestId) {
        DescriptionDraft draft = givenDraft();
        draft.requestReview(pendingRequest(reviewRequestId), authorAudit());
        return draft;
    }

    static Photo photo() {
        return new Photo(randomId(), "kosiarka-01.jpg", "image/jpeg",
                1200, 1200, 184320, 0, AT);
    }

    static Photo smallPhoto() {
        return new Photo(randomId(), "tiny.png", "image/png", 10, 10, 512, 0, AT);
    }

    static Photo hugePhoto() {
        return new Photo(randomId(), "huge.jpeg", "image/jpeg",
                1200, 1200, 11L * 1024 * 1024, 0, AT);
    }

    static Photo unsupportedPhoto() {
        return new Photo(randomId(), "scan.pdf", "application/pdf", 1200, 1200, 1024, 0, AT);
    }

    static DomainEvent lastEvent(DescriptionDraft draft) {
        return draft.events.get(draft.events.size() - 1);
    }
}
