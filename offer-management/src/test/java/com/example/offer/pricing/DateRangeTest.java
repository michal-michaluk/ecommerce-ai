package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import static com.example.offer.pricing.PricingFixture.between;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.openFrom;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class DateRangeTest {

    @Test
    void fromIsInclusive() {
        DateRange range = between("2019-07-01", "2019-07-31");

        assertThat(range.covers(date("2019-07-01"))).isTrue();
    }

    @Test
    void toIsExclusive() {
        DateRange range = between("2019-07-01", "2019-07-31");

        assertThat(range.covers(date("2019-07-30"))).isTrue();
        assertThat(range.covers(date("2019-07-31"))).isFalse();
    }

    @Test
    void openEndedCoversEveryDayFromItsStart() {
        DateRange range = openFrom("2019-07-01");

        assertThat(range.covers(date("2019-07-01"))).isTrue();
        assertThat(range.covers(date("2999-01-01"))).isTrue();
        assertThat(range.covers(date("2019-06-30"))).isFalse();
    }

    @Test
    void rejectsToBeforeFrom() {
        assertThatExceptionOfType(InvalidDateRange.class)
                .isThrownBy(() -> between("2019-08-01", "2019-07-01"));
    }

    @Test
    void requiresFrom() {
        assertThatNullPointerException().isThrownBy(() -> new DateRange(null, date("2019-07-01")));
    }
}
