package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static com.example.offer.pricing.PricingFixture.pln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MoneyTest {

    @Test
    void keepsValueAndCurrency() {
        Money money = pln("259.00");

        assertThat(money.value()).isEqualByComparingTo("259.00");
        assertThat(money.currency().getCurrencyCode()).isEqualTo("PLN");
    }

    @Test
    void scalesAmountToTwoDecimalsHalfUp() {
        assertThat(Money.of("259.999", "PLN").value()).isEqualByComparingTo("260.00");
        assertThat(Money.of("0.005", "PLN").value()).isEqualByComparingTo("0.01");
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatIllegalArgumentException().isThrownBy(() -> Money.of("-0.01", "PLN"));
    }

    @Test
    void requiresCurrency() {
        assertThatNullPointerException().isThrownBy(() -> Money.of("1.00", null));
    }

    @Test
    void discountRoundsOnceAtTheEnd() {
        Money discounted = pln("1.00").discountedBy(Percent.of("33.33"));

        assertThat(discounted.value()).isEqualByComparingTo("0.67");
    }

    @Test
    void discountRoundsTheFinalResultWithoutPreRoundingTheBase() {
        Money base = new Money(new BigDecimal("1.005"), Currency.getInstance("PLN"));

        Money discounted = base.discountedBy(Percent.of("10"));

        assertThat(discounted.value()).isEqualByComparingTo("0.90");
    }

    @Test
    void discountKeepsTheBaseCurrency() {
        Money discounted = Money.of("259.00", "USD").discountedBy(Percent.of("10"));

        assertThat(discounted.currency().getCurrencyCode()).isEqualTo("USD");
        assertThat(discounted.value()).isEqualByComparingTo("233.10");
    }
}
