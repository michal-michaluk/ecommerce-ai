package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import static com.example.offer.pricing.PricingFixture.basePrice;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.expiredPrice;
import static com.example.offer.pricing.PricingFixture.tenPercentDiscount;
import static org.assertj.core.api.Assertions.assertThat;

class PriceLifecycleTest {

    private final Price pr1 = expiredPrice();
    private final Price pr2 = basePrice();
    private final Discount pr3 = tenPercentDiscount();

    @Test
    void e08Row1_beforeEveryValidFrom_allEntriesAreScheduled() {
        assertThat(pr1.stateAt(date("2019-05-31"))).isEqualTo(PriceState.SCHEDULED);
        assertThat(pr2.stateAt(date("2019-05-31"))).isEqualTo(PriceState.SCHEDULED);
        assertThat(pr3.stateAt(date("2019-05-31"))).isEqualTo(PriceState.SCHEDULED);
        assertThat(pr1.editableAt(date("2019-05-31"))).isTrue();
        assertThat(pr2.editableAt(date("2019-05-31"))).isTrue();
        assertThat(pr3.editableAt(date("2019-05-31"))).isTrue();
    }

    @Test
    void e08Row2_onItsFirstValidFrom_anEntryBecomesActive() {
        assertThat(pr1.stateAt(date("2019-06-01"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr2.stateAt(date("2019-06-01"))).isEqualTo(PriceState.SCHEDULED);
        assertThat(pr3.stateAt(date("2019-06-01"))).isEqualTo(PriceState.SCHEDULED);
        assertThat(pr1.editableAt(date("2019-06-01"))).isFalse();
        assertThat(pr2.editableAt(date("2019-06-01"))).isTrue();
        assertThat(pr3.editableAt(date("2019-06-01"))).isTrue();
    }

    @Test
    void e08Row3_activeEntryIsNotEditableOnceItsRangeHasStarted() {
        assertThat(pr1.stateAt(date("2019-06-29"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr1.editableAt(date("2019-06-29"))).isFalse();
    }

    @Test
    void e08Row3_validToIsExclusive_soTheEntryIsExpiredOnItsBoundary() {
        assertThat(pr1.stateAt(date("2019-06-30"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr1.editableAt(date("2019-06-30"))).isFalse();
        assertThat(pr2.editableAt(date("2019-06-30"))).isTrue();
        assertThat(pr3.editableAt(date("2019-06-30"))).isTrue();
    }

    @Test
    void e08Row4_onTheNextValidFrom_thePreviousEntryIsExpiredAndTheNextIsActive() {
        assertThat(pr1.stateAt(date("2019-07-01"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr2.stateAt(date("2019-07-01"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr3.stateAt(date("2019-07-01"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr1.editableAt(date("2019-07-01"))).isFalse();
        assertThat(pr2.editableAt(date("2019-07-01"))).isFalse();
        assertThat(pr3.editableAt(date("2019-07-01"))).isFalse();
    }

    @Test
    void e08Row5_activeEntriesAreImmutableWhileTheirRangesCoverTheDay() {
        assertThat(pr1.stateAt(date("2019-07-30"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr2.stateAt(date("2019-07-30"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr3.stateAt(date("2019-07-30"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr2.editableAt(date("2019-07-30"))).isFalse();
        assertThat(pr3.editableAt(date("2019-07-30"))).isFalse();
    }

    @Test
    void e08Row5_validToIsExclusive_soTheDiscountIsExpiredOn2019_07_31() {
        assertThat(pr1.stateAt(date("2019-07-31"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr2.stateAt(date("2019-07-31"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr3.stateAt(date("2019-07-31"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr1.editableAt(date("2019-07-31"))).isFalse();
        assertThat(pr2.editableAt(date("2019-07-31"))).isFalse();
        assertThat(pr3.editableAt(date("2019-07-31"))).isFalse();
    }

    @Test
    void e08Row6_afterTheDiscountRangeEnds_onlyTheOpenEndedBaseStaysActive() {
        assertThat(pr1.stateAt(date("2019-08-01"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr2.stateAt(date("2019-08-01"))).isEqualTo(PriceState.ACTIVE);
        assertThat(pr3.stateAt(date("2019-08-01"))).isEqualTo(PriceState.EXPIRED);
        assertThat(pr1.editableAt(date("2019-08-01"))).isFalse();
        assertThat(pr2.editableAt(date("2019-08-01"))).isFalse();
        assertThat(pr3.editableAt(date("2019-08-01"))).isFalse();
    }
}
