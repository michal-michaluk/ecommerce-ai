package com.example.offer.draft;

import com.example.offer.auth.Identity;

public class ReviewerIsAuthor extends RuntimeException {
    public ReviewerIsAuthor(String productId, Identity author) {
        super("reviewer " + author.subject() + " is the author of draft " + productId);
    }
}
