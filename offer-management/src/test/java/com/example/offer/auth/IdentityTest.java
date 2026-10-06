package com.example.offer.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class IdentityTest {

    @Test
    void keepsSubject() {
        assertThat(new Identity("user-1").subject()).isEqualTo("user-1");
    }

    @Test
    void rejectsNullSubject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Identity(null))
                .withMessage("identity is required");
    }

    @Test
    void rejectsBlankSubject() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new Identity("   "))
                .withMessage("identity is required");
    }
}
