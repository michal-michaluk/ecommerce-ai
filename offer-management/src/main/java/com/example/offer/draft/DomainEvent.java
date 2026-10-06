package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DomainEvent.BlankDraftCreated.class,         name = "BlankDraftCreated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionUpdated.class,        name = "DescriptionUpdated_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PhotoAddedInRightFormats.class,  name = "PhotoAddedInRightFormats_v1"),
        @JsonSubTypes.Type(value = DomainEvent.PhotoRemoved.class,              name = "PhotoRemoved_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionPendingReview.class,  name = "DescriptionPendingReview_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReviewApproved.class, name = "DescriptionReviewApproved_v1"),
        @JsonSubTypes.Type(value = DomainEvent.DescriptionReviewRejected.class, name = "DescriptionReviewRejected_v1")
})
public interface DomainEvent {

    record BlankDraftCreated(String productId, String version, int revision, DraftState state,
                             Title title, Description description, DraftAttributes attributes,
                             List<Photo> photos, String basedOnVersion,
                             ReviewRequest review, Audit audit) implements DomainEvent {
        public BlankDraftCreated {
            photos = List.copyOf(photos);
        }
    }

    record DescriptionUpdated(String productId, int revision, Title title,
                              Description description, DraftAttributes attributes,
                              Audit audit) implements DomainEvent {
    }

    record PhotoAddedInRightFormats(String productId, Photo photo, Audit audit) implements DomainEvent {
    }

    record PhotoRemoved(String productId, String photoId, Audit audit) implements DomainEvent {
    }

    record DescriptionPendingReview(String productId, ReviewRequest review, Audit audit) implements DomainEvent {
    }

    record DescriptionReviewApproved(String productId, ReviewRequest review, Audit audit) implements DomainEvent {
    }

    record DescriptionReviewRejected(String productId, ReviewRequest review, String reason, Audit audit) implements DomainEvent {
    }
}
