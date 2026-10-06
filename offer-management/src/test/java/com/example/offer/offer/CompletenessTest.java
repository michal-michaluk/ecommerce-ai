package com.example.offer.offer;

import com.example.offer.draft.DraftAttributes;
import com.example.offer.draft.DraftSnapshot;
import com.example.offer.draft.Description;
import com.example.offer.draft.Photo;
import com.example.offer.draft.Title;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Element 05 — every row of the scenario table plus the catalogue rules RULE-36..44. */
class CompletenessTest {

    private final CompletenessPolicy policy = CompletenessPolicy.standard();

    @Test
    void row1_titleDescriptionAndTwoPhotosWithActivePriceIsComplete() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 2, PricePresence.ACTIVE);

        assertThat(completeness.complete()).isTrue();
        assertThat(completeness.missing()).isEmpty();
    }

    @Test
    void row2_scheduledPriceIsComplete() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 2, PricePresence.SCHEDULED);

        assertThat(completeness.complete()).isTrue();
        assertThat(completeness.missing()).isEmpty();
    }

    @Test
    void row3_expiredPriceIsMissingPrice() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 2, PricePresence.EXPIRED);

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("PRICE_REQUIRED");
    }

    @Test
    void row4_noPriceIsMissingPrice() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 2, PricePresence.NONE);

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("PRICE_REQUIRED");
    }

    @Test
    void row5_noPhotoIsMissingPhoto() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 0, PricePresence.ACTIVE);

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("PHOTO_REQUIRED");
    }

    @Test
    void row6_blankDescriptionIsMissingDescription() {
        Completeness completeness = evaluate("Prosto z półki", "", 2, PricePresence.ACTIVE);

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("DESCRIPTION_REQUIRED");
    }

    @Test
    void row7_everythingBlankIsMissingInCatalogueOrder() {
        Completeness completeness = evaluate("", "", 0, PricePresence.NONE);

        assertThat(completeness.complete()).isFalse();
        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("TITLE_REQUIRED", "DESCRIPTION_REQUIRED", "PHOTO_REQUIRED", "PRICE_REQUIRED");
    }

    @Test
    void row8_exactlyOnePhotoIsEnough() {
        Completeness completeness = evaluate("Prosto z półki", "Opis", 1, PricePresence.ACTIVE);

        assertThat(completeness.complete()).isTrue();
        assertThat(completeness.missing()).isEmpty();
    }

    @Test
    void onlyUnmetItemsAreReported() {                                          // RULE-42
        Completeness completeness = evaluate("Prosto z półki", "", 2, PricePresence.EXPIRED);

        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("DESCRIPTION_REQUIRED", "PRICE_REQUIRED");
    }

    @Test
    void missingOrderFollowsTheCatalogueNotThePredicateOrder() {               // RULE-36, RULE-43
        CompletenessPolicy reordered = CompletenessPolicy.of(List.of(
                "PRICE_REQUIRED", "PHOTO_REQUIRED", "DESCRIPTION_REQUIRED", "TITLE_REQUIRED"));
        Completeness completeness = reordered.evaluate(new RequirementContext("", "", 0, PricePresence.NONE));

        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("PRICE_REQUIRED", "PHOTO_REQUIRED", "DESCRIPTION_REQUIRED", "TITLE_REQUIRED");
    }

    @Test
    void theEnabledSetComesFromConfiguration() {                               // RULE-36
        CompletenessPolicy reduced = CompletenessPolicy.of(List.of("TITLE_REQUIRED"));
        Completeness completeness = reduced.evaluate(new RequirementContext("", "", 0, PricePresence.NONE));

        assertThat(completeness.missing()).extracting(Completeness.MissingRequirement::code)
                .containsExactly("TITLE_REQUIRED");
    }

    @Test
    void advisoryIssuesAreOutsideCompletenessAndMissing() {                     // RULE-44
        TextCheck checker = draft -> List.of(new TextIssue("double space", TextIssue.Severity.ADVISORY));
        CompletenessPolicy withChecker = new CompletenessPolicy(RequirementCatalogue.standard(), checker);

        Completeness completeness = withChecker.evaluate(draftWithTwoPhotos(), PricePresence.ACTIVE);

        assertThat(completeness.complete()).isTrue();
        assertThat(completeness.missing()).isEmpty();
        assertThat(completeness.issues()).singleElement()
                .extracting(TextIssue::message).isEqualTo("double space");
    }

    @Test
    void withoutACheckerTheAdvisoryListIsEmpty() {                             // null-object default
        Completeness completeness = policy.evaluate(draftWithTwoPhotos(), PricePresence.ACTIVE);

        assertThat(completeness.issues()).isEmpty();
    }

    private Completeness evaluate(String title, String description, int photoCount, PricePresence price) {
        return policy.evaluate(new RequirementContext(title, description, photoCount, price));
    }

    private static DraftSnapshot draftWithTwoPhotos() {
        return new DraftSnapshot("p-2019-0442", "v1", com.example.offer.draft.DraftState.EDITING, 1,
                new Title("Kosiarka ręczna 340"), new Description("Solidna kosiarka ręczna."),
                new DraftAttributes(null, null),
                List.of(photo("ph-1"), photo("ph-2")), null, null, OfferFixture.audit());
    }

    private static Photo photo(String photoId) {
        return new Photo(photoId, photoId + ".jpg", "image/jpeg", 1200, 1200, 1024, 0, OfferFixture.AT);
    }
}
