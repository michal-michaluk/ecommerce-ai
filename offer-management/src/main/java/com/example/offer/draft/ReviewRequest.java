package com.example.offer.draft;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

public record ReviewRequest(String reviewRequestId, Identity author, Identity decidedBy, Instant at) {

    static ReviewRequest requested(String reviewRequestId, Audit audit) {
        return new ReviewRequest(reviewRequestId, audit.who(), null, audit.at());
    }

    static ReviewRequest decided(ReviewRequest request, Identity decidedBy, Instant at) {
        return new ReviewRequest(request.reviewRequestId(), request.author(), decidedBy, at);
    }

    @JsonIgnore
    public boolean isPending() {
        return decidedBy == null;
    }

    @JsonIgnore
    public boolean isDecided() {
        return decidedBy != null;
    }
}
