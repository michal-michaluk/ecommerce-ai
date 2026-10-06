package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class PercentTest {

    @Test
    void acceptsADecimalInsideTheOpenRange() {
        assertThat(Percent.of("99.99").value()).isEqualByComparingTo("99.99");
        assertThat(Percent.of("0.01").value()).isEqualByComparingTo("0.01");
    }

    @Test
    void rejectsZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percent.of("0"));
    }

    @Test
    void rejectsAHundred() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percent.of("100"));
    }

    @Test
    void rejectsNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percent.of("-5"));
    }

    @Test
    void rejectsMoreThanTwoDecimalPlaces() {
        assertThatIllegalArgumentException().isThrownBy(() -> Percent.of("10.005"));
    }
}
