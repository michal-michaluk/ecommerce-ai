package com.example.offer.draft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class TitleTest {

    @Test
    void keepsValue() {
        assertThat(new Title("Kosiarka").value()).isEqualTo("Kosiarka");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Title(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Title("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
