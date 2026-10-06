package com.example.offer.draft;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class DescriptionTest {

    @Test
    void keepsValue() {
        assertThat(new Description("Opis").value()).isEqualTo("Opis");
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> new Description(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> new Description("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
