package com.example.offer.draft;

import com.example.offer.auth.Audit;
import org.junit.jupiter.api.Test;

import static com.example.offer.draft.DraftFixture.AUTHOR;
import static com.example.offer.draft.DraftFixture.AT;
import static com.example.offer.draft.DraftFixture.REVIEWER;
import static com.example.offer.draft.DraftFixture.authorAudit;
import static com.example.offer.draft.DraftFixture.description;
import static com.example.offer.draft.DraftFixture.givenDraft;
import static com.example.offer.draft.DraftFixture.givenDraftInReview;
import static com.example.offer.draft.DraftFixture.hugePhoto;
import static com.example.offer.draft.DraftFixture.lastEvent;
import static com.example.offer.draft.DraftFixture.pendingRequest;
import static com.example.offer.draft.DraftFixture.photo;
import static com.example.offer.draft.DraftFixture.reviewerAudit;
import static com.example.offer.draft.DraftFixture.smallPhoto;
import static com.example.offer.draft.DraftFixture.title;
import static com.example.offer.draft.DraftFixture.unsupportedPhoto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DescriptionDraftTest {

    @Test
    void createsBlankDraftEditingWithFirstRevisionAndEvent() {
        DescriptionDraft draft = givenDraft();

        assertThat(draft.events).singleElement()
                .isInstanceOf(DomainEvent.BlankDraftCreated.class);
        DraftSnapshot snapshot = draft.toDraftSnapshot();
        assertThat(snapshot.state()).isEqualTo(DraftState.EDITING);
        assertThat(snapshot.revision()).isEqualTo(1);
        assertThat(snapshot.title()).isEqualTo(title());
        assertThat(snapshot.description()).isNull();
        assertThat(snapshot.photos()).isEmpty();
    }

    @Test
    void editReplacesProvidedFieldsAndEmitsDescriptionUpdated() {
        DescriptionDraft draft = givenDraft();

        draft.edit(UpdateDraft.builder().description(description()).build(), authorAudit());

        DraftSnapshot snapshot = draft.toDraftSnapshot();
        assertThat(snapshot.description()).isEqualTo(description());
        assertThat(snapshot.title()).isEqualTo(title());
        assertThat(snapshot.revision()).isEqualTo(2);
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.DescriptionUpdated.class);
    }

    @Test
    void editOfUnchangedFieldsIsIdempotentAndEmitsNothing() {
        DescriptionDraft draft = givenDraft();
        Audit initialLastChange = draft.toDraftSnapshot().lastChange();

        draft.edit(UpdateDraft.builder().title(title()).build(), new Audit(AUTHOR, AT.plusSeconds(60)));

        assertThat(draft.events).hasSize(1);
        assertThat(draft.toDraftSnapshot().revision()).isEqualTo(1);
        assertThat(draft.toDraftSnapshot().lastChange()).isEqualTo(initialLastChange);
    }

    @Test
    void editStoresTheActingAuditAsLastChange() {
        DescriptionDraft draft = givenDraft();
        Audit second = new Audit(AUTHOR, AT.plusSeconds(60));

        draft.edit(UpdateDraft.builder().description(description()).build(), second);

        assertThat(draft.toDraftSnapshot().lastChange()).isEqualTo(second);
    }

    @Test
    void attachPhotoAndDetachPhotoStoreTheActingAudit() {
        DescriptionDraft draft = givenDraft();
        Photo p = photo();
        Audit second = new Audit(AUTHOR, AT.plusSeconds(60));
        Audit third = new Audit(AUTHOR, AT.plusSeconds(120));

        draft.attachPhoto(p, second);
        assertThat(draft.toDraftSnapshot().lastChange()).isEqualTo(second);

        draft.detachPhoto(p.photoId(), third);
        assertThat(draft.toDraftSnapshot().lastChange()).isEqualTo(third);
    }

    @Test
    void editIsRejectedWhileInReview() {
        DescriptionDraft draft = givenDraftInReview("rr-1");

        assertThatThrownBy(() -> draft.edit(UpdateDraft.builder().description(description()).build(), authorAudit()))
                .isInstanceOf(DraftNotEditable.class);
    }

    @Test
    void attachPhotoIsRejectedWhileInReview() {
        DescriptionDraft draft = givenDraftInReview("rr-1");

        assertThatThrownBy(() -> draft.attachPhoto(photo(), authorAudit()))
                .isInstanceOf(DraftNotEditable.class);
    }

    @Test
    void detachPhotoIsRejectedWhileInReview() {
        DescriptionDraft draft = givenDraft();
        Photo p = photo();
        draft.attachPhoto(p, authorAudit());
        draft.requestReview(pendingRequest("rr-1"), authorAudit());

        assertThatThrownBy(() -> draft.detachPhoto(p.photoId(), authorAudit()))
                .isInstanceOf(DraftNotEditable.class);
    }

    @Test
    void attachPhotoAssignsContiguousPositionAndEmits() {
        DescriptionDraft draft = givenDraft();

        draft.attachPhoto(photo(), authorAudit());
        draft.attachPhoto(photo(), authorAudit());

        assertThat(draft.toDraftSnapshot().photos())
                .extracting(Photo::position).containsExactly(0, 1);
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.PhotoAddedInRightFormats.class);
    }

    @Test
    void attachPhotoRejectsUnsupportedFormat() {
        DescriptionDraft draft = givenDraft();

        assertThatThrownBy(() -> draft.attachPhoto(unsupportedPhoto(), authorAudit()))
                .isInstanceOf(PhotoFormatUnsupported.class);
        assertThat(draft.events).hasSize(1);
    }

    @Test
    void attachPhotoRejectsTooSmall() {
        DescriptionDraft draft = givenDraft();

        assertThatThrownBy(() -> draft.attachPhoto(smallPhoto(), authorAudit()))
                .isInstanceOf(PhotoTooSmall.class);
    }

    @Test
    void attachPhotoRejectsTooLarge() {
        DescriptionDraft draft = givenDraft();

        assertThatThrownBy(() -> draft.attachPhoto(hugePhoto(), authorAudit()))
                .isInstanceOf(PhotoTooLarge.class);
    }

    @Test
    void detachPhotoRenumbersRemainingAndEmits() {
        DescriptionDraft draft = givenDraft();
        Photo first = photo();
        Photo second = photo();
        draft.attachPhoto(first, authorAudit());
        draft.attachPhoto(second, authorAudit());

        draft.detachPhoto(first.photoId(), authorAudit());

        assertThat(draft.toDraftSnapshot().photos())
                .extracting(Photo::photoId).containsExactly(second.photoId());
        assertThat(draft.toDraftSnapshot().photos())
                .extracting(Photo::position).containsExactly(0);
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.PhotoRemoved.class);
    }

    @Test
    void detachOfUnknownPhotoIsIdempotentAndEmitsNothing() {
        DescriptionDraft draft = givenDraft();

        draft.detachPhoto("missing", authorAudit());

        assertThat(draft.events).hasSize(1);
    }

    @Test
    void requestReviewIsAllowedWithMissingItems() {
        DescriptionDraft draft = givenDraft();

        draft.requestReview(pendingRequest("rr-1"), authorAudit());

        DraftSnapshot snapshot = draft.toDraftSnapshot();
        assertThat(snapshot.state()).isEqualTo(DraftState.IN_REVIEW);
        assertThat(snapshot.review().isPending()).isTrue();
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.DescriptionPendingReview.class);
    }

    @Test
    void requestReviewRejectsForgedAuthor() {
        DescriptionDraft draft = givenDraft();
        ReviewRequest forged = new ReviewRequest("rr-1", REVIEWER, null, AT);

        assertThatThrownBy(() -> draft.requestReview(forged, authorAudit()))
                .isInstanceOf(ActorMismatch.class);
    }

    @Test
    void requestReviewIsRejectedWhileAlreadyInReview() {
        DescriptionDraft draft = givenDraftInReview("rr-1");

        assertThatThrownBy(() -> draft.requestReview(pendingRequest("rr-2"), authorAudit()))
                .isInstanceOf(DraftNotEditable.class);
    }

    @Test
    void approveMovesDraftToApprovedState() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);

        draft.approve(decision, reviewerAudit());

        DraftSnapshot snapshot = draft.toDraftSnapshot();
        assertThat(snapshot.state()).isEqualTo(DraftState.APPROVED);
        assertThat(snapshot.review().isDecided()).isTrue();
        assertThat(snapshot.lastChange()).isEqualTo(reviewerAudit());
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.DescriptionReviewApproved.class);
    }

    @Test
    void approveRejectsADecisionForADifferentReview() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest other = ReviewRequest.decided(pendingRequest("rr-2"), REVIEWER, AT);

        assertThatThrownBy(() -> draft.approve(other, reviewerAudit()))
                .isInstanceOf(ReviewNotPending.class);
    }

    @Test
    void approveIsRejectedWhenReviewerIsTheAuthor() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest selfReview = ReviewRequest.decided(pendingRequest("rr-1"), AUTHOR, AT);

        assertThatThrownBy(() -> draft.approve(selfReview, authorAudit()))
                .isInstanceOf(ReviewerIsAuthor.class);
    }

    @Test
    void approveRejectsActorThatIsNotTheDecider() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);

        assertThatThrownBy(() -> draft.approve(decision, authorAudit()))
                .isInstanceOf(ActorMismatch.class);
    }

    @Test
    void approveWithoutPendingReviewIsRejected() {
        DescriptionDraft draft = givenDraft();
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);

        assertThatThrownBy(() -> draft.approve(decision, reviewerAudit()))
                .isInstanceOf(ReviewNotPending.class);
    }

    @Test
    void aDecisionIsTerminal() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);
        draft.approve(decision, reviewerAudit());

        assertThatThrownBy(() -> draft.approve(decision, reviewerAudit()))
                .isInstanceOf(ReviewNotPending.class);
    }

    @Test
    void rejectReturnsDraftToEditing() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);

        draft.reject(decision, "Opis niekompletny.", reviewerAudit());

        assertThat(draft.toDraftSnapshot().state()).isEqualTo(DraftState.EDITING);
        assertThat(lastEvent(draft)).isInstanceOf(DomainEvent.DescriptionReviewRejected.class);
        draft.edit(UpdateDraft.builder().description(description()).build(), authorAudit());
        assertThat(draft.toDraftSnapshot().revision()).isEqualTo(2);
    }

    @Test
    void rejectIsRejectedWhenReviewerIsTheAuthor() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest selfReview = ReviewRequest.decided(pendingRequest("rr-1"), AUTHOR, AT);

        assertThatThrownBy(() -> draft.reject(selfReview, "no", authorAudit()))
                .isInstanceOf(ReviewerIsAuthor.class);
    }

    @Test
    void rejectRejectsActorThatIsNotTheDecider() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);

        assertThatThrownBy(() -> draft.reject(decision, "no", authorAudit()))
                .isInstanceOf(ActorMismatch.class);
    }

    @Test
    void rejectRejectsADecisionForADifferentReview() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest other = ReviewRequest.decided(pendingRequest("rr-2"), REVIEWER, AT);

        assertThatThrownBy(() -> draft.reject(other, "no", reviewerAudit()))
                .isInstanceOf(ReviewNotPending.class);
    }

    @Test
    void editIsRejectedAfterApproval() {
        DescriptionDraft draft = givenDraftInReview("rr-1");
        ReviewRequest decision = ReviewRequest.decided(pendingRequest("rr-1"), REVIEWER, AT);
        draft.approve(decision, reviewerAudit());

        assertThatThrownBy(() -> draft.edit(UpdateDraft.builder().description(description()).build(), authorAudit()))
                .isInstanceOf(DraftNotEditable.class);
    }
}
