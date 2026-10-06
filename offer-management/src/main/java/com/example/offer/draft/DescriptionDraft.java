package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@AllArgsConstructor
class DescriptionDraft {

    private static final PhotoFormatPolicy PHOTO_POLICY = PhotoFormatPolicy.standard();

    private final String productId;
    private final String version;
    final List<DomainEvent> events;

    private DraftState state;
    private int revision;
    private Title title;
    private Description description;
    private DraftAttributes attributes;
    private List<Photo> photos;
    private String basedOnVersion;
    private ReviewRequest review;
    private Audit lastChange;

    static DescriptionDraft newDraft(String productId, String version, Title title, Audit audit) {
        Objects.requireNonNull(title);
        Objects.requireNonNull(audit);
        DescriptionDraft draft = new DescriptionDraft(productId, version, new ArrayList<>(),
                DraftState.EDITING, 1, title, null, DraftAttributes.empty(),
                List.of(), null, null, audit);
        draft.events.add(new DomainEvent.BlankDraftCreated(productId, version, draft.revision,
                draft.state, draft.title, draft.description, draft.attributes,
                List.copyOf(draft.photos), draft.basedOnVersion, draft.review, audit));
        return draft;
    }

    void edit(UpdateDraft update, Audit audit) {
        checkEditable();
        Title newTitle = firstNonNull(update.title(), title);
        Description newDescription = firstNonNull(update.description(), description);
        DraftAttributes newAttributes = firstNonNull(update.attributes(), attributes);
        if (Objects.equals(newTitle, title) && Objects.equals(newDescription, description)
                && Objects.equals(newAttributes, attributes)) {
            return;
        }
        this.title = newTitle;
        this.description = newDescription;
        this.attributes = newAttributes;
        this.revision++;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionUpdated(productId, revision, newTitle,
                newDescription, newAttributes, audit));
    }

    void attachPhoto(Photo photo, Audit audit) {
        checkEditable();
        PHOTO_POLICY.check(photo);
        Photo positioned = photo.toBuilder().position(photos.size()).build();
        this.photos = List.copyOf(Stream.concat(photos.stream(), Stream.of(positioned)).toList());
        this.lastChange = audit;
        events.add(new DomainEvent.PhotoAddedInRightFormats(productId, positioned, audit));
    }

    void detachPhoto(String photoId, Audit audit) {
        checkEditable();
        List<Photo> remaining = photos.stream().filter(p -> !p.photoId().equals(photoId)).toList();
        if (remaining.size() == photos.size()) {
            return;
        }
        this.photos = renumber(remaining);
        this.lastChange = audit;
        events.add(new DomainEvent.PhotoRemoved(productId, photoId, audit));
    }

    void requestReview(ReviewRequest request, Audit audit) {
        checkEditable();
        checkSamePerson(request.author(), audit.who());
        this.state = DraftState.IN_REVIEW;
        this.review = request;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionPendingReview(productId, request, audit));
    }

    void approve(ReviewRequest decision, Audit audit) {
        checkPending(decision.reviewRequestId());
        checkSamePerson(decision.decidedBy(), audit.who());
        checkDifferentPerson(decision.author(), decision.decidedBy());
        this.state = DraftState.APPROVED;
        this.review = decision;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionReviewApproved(productId, decision, audit));
    }

    void reject(ReviewRequest decision, String reason, Audit audit) {
        checkPending(decision.reviewRequestId());
        checkSamePerson(decision.decidedBy(), audit.who());
        checkDifferentPerson(decision.author(), decision.decidedBy());
        this.state = DraftState.EDITING;
        this.review = decision;
        this.lastChange = audit;
        events.add(new DomainEvent.DescriptionReviewRejected(productId, decision, reason, audit));
    }

    DraftSnapshot toDraftSnapshot() {
        return new DraftSnapshot(productId, version, state, revision, title, description,
                attributes, List.copyOf(photos), basedOnVersion, review, lastChange);
    }

    private void checkEditable() {
        if (state != DraftState.EDITING) {
            throw new DraftNotEditable(productId, state);
        }
    }

    private void checkPending(String reviewRequestId) {
        if (review == null || review.isDecided() || !review.reviewRequestId().equals(reviewRequestId)) {
            throw new ReviewNotPending(reviewRequestId);
        }
    }

    private void checkDifferentPerson(Identity author, Identity decidedBy) {
        if (Objects.equals(author, decidedBy)) {
            throw new ReviewerIsAuthor(productId, author);
        }
    }

    private void checkSamePerson(Identity expected, Identity actual) {
        if (!Objects.equals(expected, actual)) {
            throw new ActorMismatch(expected, actual);
        }
    }

    private static List<Photo> renumber(List<Photo> photos) {
        List<Photo> renumbered = new ArrayList<>(photos.size());
        for (int i = 0; i < photos.size(); i++) {
            renumbered.add(photos.get(i).toBuilder().position(i).build());
        }
        return List.copyOf(renumbered);
    }

    private static <T> T firstNonNull(T candidate, T fallback) {
        return candidate != null ? candidate : fallback;
    }
}
