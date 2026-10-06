package com.example.offer.offer;

import com.example.offer.auth.Identity;
import com.example.offer.tools.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Element 07 — every rule row of D1..D5 plus the cross-decision codification rules. */
class DecisionsTest {

    private final Decisions decisions = new Decisions();

    private static final Identity AUTHOR = new Identity("a.kowalska");
    private static final Identity REVIEWER = new Identity("m.nowak");
    private static final LocalDate BUSINESS_DATE = LocalDate.parse("2019-06-20");
    private static final LocalDate CREATED_ON = LocalDate.parse("2019-03-11");

    // D1 — description quality policy

    @Test
    void d1q1_nothingMissingAndNoIssuesPasses() {                              // RULE-48
        Decisions.Quality quality = decisions.quality(complete());

        assertThat(quality.outcome()).isEqualTo(Decisions.QualityOutcome.PASS);
        assertThat(quality.blocking()).isEmpty();
    }

    @Test
    void d1q2_advisoryIssuesStillPassWithIssues() {                            // RULE-48
        Decisions.Quality quality = decisions.quality(withIssues());

        assertThat(quality.outcome()).isEqualTo(Decisions.QualityOutcome.PASS_WITH_ISSUES);
        assertThat(quality.blocking()).isEmpty();
    }

    @Test
    void d1q3_missingItemsFailAndBlock() {                                     // RULE-48
        Decisions.Quality quality = decisions.quality(incomplete(price()));

        assertThat(quality.outcome()).isEqualTo(Decisions.QualityOutcome.FAIL);
        assertThat(quality.blocking()).containsExactly(price());
    }

    // D2 — publish guard

    @Test
    void d2p1_existingApprovedCompleteVersionIsAllowed() {                     // RULE-50
        Decisions.Decision<Void> decision = decisions.publishGuard(true, true, complete());

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    void d2p2_incompleteVersionIsBlockedWithTheMissingItems() {                // RULE-50
        Decisions.Decision<Void> decision = decisions.publishGuard(true, true, incomplete(price()));

        assertThat(decision.denial()).isEqualTo(ErrorCode.PUBLICATION_BLOCKED);
        assertThat(decision.blocking()).containsExactly(price());
    }

    @Test
    void d2p3_unapprovedVersionIsRejected() {
        Decisions.Decision<Void> decision = decisions.publishGuard(true, false, incomplete(price()));

        assertThat(decision.denial()).isEqualTo(ErrorCode.VERSION_NOT_APPROVED);
    }

    @Test
    void d2p4_missingVersionIsNotFound() {
        Decisions.Decision<Void> decision = decisions.publishGuard(false, true, complete());

        assertThat(decision.denial()).isEqualTo(ErrorCode.VERSION_NOT_FOUND);
    }

    @Test
    void d2_mostSpecificFailureWins_notFoundBeforeNotApprovedBeforeBlocked() {
        assertThat(decisions.publishGuard(false, false, incomplete(price())).denial())
                .isEqualTo(ErrorCode.VERSION_NOT_FOUND);
        assertThat(decisions.publishGuard(true, false, incomplete(price())).denial())
                .isEqualTo(ErrorCode.VERSION_NOT_APPROVED);
    }

    // D3 — review request guard

    @Test
    void d3v1_editingWithNothingMissingIsAllowed() {                           // RULE-53
        Decisions.Decision<Integer> decision = decisions.reviewRequestGuard(true, false, 0);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.value()).isZero();
    }

    @Test
    void d3v2_editingWithMissingItemsIsAllowedAndTheCountTravels() {           // RULE-52
        Decisions.Decision<Integer> decision = decisions.reviewRequestGuard(true, false, 2);

        assertThat(decision.isAllowed()).isTrue();
        assertThat(decision.value()).isEqualTo(2);
    }

    @Test
    void d3v3_alreadyInReviewIsRejected() {                                    // RULE-53
        Decisions.Decision<Integer> decision = decisions.reviewRequestGuard(true, true, 2);

        assertThat(decision.denial()).isEqualTo(ErrorCode.REVIEW_ALREADY_PENDING);
    }

    @Test
    void d3v4_noDraftIsNotFound() {
        Decisions.Decision<Integer> decision = decisions.reviewRequestGuard(false, false, 0);

        assertThat(decision.denial()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    // D4 — publication timing

    @Test
    void d4t1_nullAvailableFromPublishesNow() {
        Decisions.Decision<PublicationState> decision = decisions.publicationTiming(null, BUSINESS_DATE, CREATED_ON);

        assertThat(decision.value()).isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    void d4t2_todayOrPastPublishesImmediately() {                              // RULE-54
        assertThat(decisions.publicationTiming(BUSINESS_DATE, BUSINESS_DATE, CREATED_ON).value())
                .isEqualTo(PublicationState.PUBLISHED);
        assertThat(decisions.publicationTiming(BUSINESS_DATE.minusDays(5), BUSINESS_DATE, CREATED_ON).value())
                .isEqualTo(PublicationState.PUBLISHED);
    }

    @Test
    void d4t3_futureAvailableFromSchedules() {                                 // RULE-55
        Decisions.Decision<PublicationState> decision =
                decisions.publicationTiming(BUSINESS_DATE.plusDays(10), BUSINESS_DATE, CREATED_ON);

        assertThat(decision.value()).isEqualTo(PublicationState.SCHEDULED);
    }

    @Test
    void d4t4_availableFromBeforeCreationIsInvalid() {
        Decisions.Decision<PublicationState> decision =
                decisions.publicationTiming(CREATED_ON.minusDays(1), BUSINESS_DATE, CREATED_ON);

        assertThat(decision.denial()).isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    // D5 — separation of duties

    @Test
    void d5s0_theReviewIsNotPending() {
        Decisions.Decision<Void> decision = decisions.separationOfDuties(false, AUTHOR, REVIEWER);

        assertThat(decision.denial()).isEqualTo(ErrorCode.REVIEW_NOT_PENDING);
    }

    @Test
    void d5s1_differentPersonMayDecide() {                                     // RULE-56
        Decisions.Decision<Void> decision = decisions.separationOfDuties(true, AUTHOR, REVIEWER);

        assertThat(decision.isAllowed()).isTrue();
    }

    @Test
    void d5s2_theAuthorCannotDecideTheirOwnReview() {                          // RULE-57
        Decisions.Decision<Void> decision = decisions.separationOfDuties(true, AUTHOR, AUTHOR);

        assertThat(decision.denial()).isEqualTo(ErrorCode.REVIEWER_IS_AUTHOR);
    }

    // cross-decision rules

    @Test
    void everyDenyCarriesAnElement02ErrorCode() {                              // RULE-58
        assertThat(decisions.publishGuard(false, true, complete()).denial()).isNotNull();
        assertThat(decisions.reviewRequestGuard(false, false, 0).denial()).isNotNull();
        assertThat(decisions.publicationTiming(CREATED_ON.minusDays(1), BUSINESS_DATE, CREATED_ON).denial()).isNotNull();
        assertThat(decisions.separationOfDuties(false, AUTHOR, REVIEWER).denial()).isNotNull();
    }

    @Test
    void decisionsAreDeterministic() {                                         // RULE-59
        assertThat(decisions.publicationTiming(BUSINESS_DATE.plusDays(1), BUSINESS_DATE, CREATED_ON))
                .isEqualTo(decisions.publicationTiming(BUSINESS_DATE.plusDays(1), BUSINESS_DATE, CREATED_ON));
        assertThat(decisions.quality(incomplete(price())))
                .isEqualTo(decisions.quality(incomplete(price())));
    }

    private static Completeness complete() {
        return new Completeness(true, List.of(), List.of());
    }

    private static Completeness withIssues() {
        return new Completeness(true, List.of(),
                List.of(new TextIssue("double space", TextIssue.Severity.ADVISORY)));
    }

    private static Completeness incomplete(Completeness.MissingRequirement... missing) {
        return new Completeness(false, List.of(missing), List.of());
    }

    private static Completeness.MissingRequirement price() {
        return new Completeness.MissingRequirement("PRICE_REQUIRED", "Price valid for a date range");
    }
}
