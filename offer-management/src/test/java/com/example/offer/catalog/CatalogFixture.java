package com.example.offer.catalog;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.draft.DraftAttributes;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.DraftState;
import com.example.offer.draft.Description;
import com.example.offer.draft.Photo;
import com.example.offer.draft.ReviewRequest;
import com.example.offer.draft.Title;
import com.example.offer.offer.OfferPresence;
import com.example.offer.offer.ProductSnapshot;
import com.example.offer.offer.Publication;
import com.example.offer.pricing.DateRange;
import com.example.offer.pricing.Money;
import com.example.offer.pricing.Price;
import com.example.offer.pricing.PriceScheduleSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class CatalogFixture {

    static final Identity AUTHOR = new Identity("a.kowalska");
    static final Identity REVIEWER = new Identity("m.nowak");
    static final Instant AT = Instant.parse("2019-10-02T09:10:00Z");
    static final String CATEGORY = "Ogród";

    private CatalogFixture() {
    }

    static String id() {
        return "p-" + UUID.randomUUID();
    }

    static Audit audit() {
        return new Audit(AUTHOR, AT);
    }

    static Audit audit(String subject, Instant at) {
        return new Audit(new Identity(subject), at);
    }

    static DraftSnapshot draft(String productId, DraftState state, ReviewRequest review) {
        return new DraftSnapshot(productId, "v1", state, 1,
                new Title("Kosiarka ręczna 340"),
                new Description("Solidna kosiarka ręczna do trawy i chwastów."),
                new DraftAttributes(CATEGORY, null),
                List.of(new Photo("ph-1", "kosiarka-01.jpg", "image/jpeg", 1200, 1200, 184320, 0, AT)),
                null, review, audit());
    }

    static DraftSnapshot draftWithoutPrice(String productId, DraftState state) {
        return draft(productId, state, null);
    }

    static ReviewRequest pendingReview(String reviewRequestId) {
        return new ReviewRequest(reviewRequestId, AUTHOR, null, AT);
    }

    static ReviewRequest decidedReview(String reviewRequestId, Instant decidedAt) {
        return new ReviewRequest(reviewRequestId, AUTHOR, REVIEWER, decidedAt);
    }

    static ProductSnapshot product(String productId, OfferPresence presence, List<Publication> publications) {
        return new ProductSnapshot(productId, presence, null, null,
                com.example.offer.offer.DraftState.EDITING, AT, audit(), List.of(), publications);
    }

    static Publication publication(String productId, String version, LocalDate availableFrom) {
        boolean future = availableFrom.isAfter(LocalDate.now());
        return new Publication("pub-" + version, productId, version, availableFrom, AT,
                future ? null : AT, false);
    }

    static PriceScheduleSnapshot activePrice(String productId) {
        return new PriceScheduleSnapshot(productId,
                List.of(new Price("pr-1", productId, Money.of("259.00", "PLN"),
                        DateRange.from(LocalDate.now().minusDays(10)))),
                List.of());
    }
}
