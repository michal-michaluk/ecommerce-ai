package com.example.offer.draft;

public class ReviewNotPending extends RuntimeException {
    public ReviewNotPending(String reviewRequestId) {
        super("no pending review request " + reviewRequestId);
    }
}
