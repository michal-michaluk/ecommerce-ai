package com.example.offer.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class AuditTest {

    @Test
    void keepsActorAndInstant() {
        Identity who = new Identity("user-1");
        Instant at = Instant.parse("2025-01-01T00:00:00Z");

        Audit audit = new Audit(who, at);

        assertThat(audit.who()).isEqualTo(who);
        assertThat(audit.at()).isEqualTo(at);
    }

    @Test
    void rejectsNullIdentity() {
        assertThatNullPointerException().isThrownBy(() -> new Audit(null, Instant.now()));
    }

    @Test
    void rejectsNullInstant() {
        assertThatNullPointerException().isThrownBy(() -> new Audit(new Identity("user-1"), null));
    }
}
