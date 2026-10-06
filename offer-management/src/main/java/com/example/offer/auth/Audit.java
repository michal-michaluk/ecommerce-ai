package com.example.offer.auth;

import java.time.Instant;
import java.util.Objects;

/** Who changed the state and when — attached to every state-modifying operation. */
public record Audit(Identity who, Instant at) {
    public Audit {
        Objects.requireNonNull(who);
        Objects.requireNonNull(at);
    }
}
