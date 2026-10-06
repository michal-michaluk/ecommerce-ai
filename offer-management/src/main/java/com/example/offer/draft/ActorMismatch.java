package com.example.offer.draft;

import com.example.offer.auth.Identity;

public class ActorMismatch extends RuntimeException {
    public ActorMismatch(Identity expected, Identity actual) {
        super("expected actor " + expected + " but got " + actual);
    }
}
