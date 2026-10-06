package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.example.offer.pricing.PricingFixture.basePrice;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.expiredPrice;
import static com.example.offer.pricing.PricingFixture.pln;
import static com.example.offer.pricing.PricingFixture.tenPercentDiscount;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EffectivePriceTest {

    @Test
    void e06Row1_noBasePriceCoversTheDate_yieldsNoEffectivePrice() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(tenPercentDiscount()), date("2019-06-15"));

        assertThat(price).isEmpty();
    }

    @Test
    void e06Row1_documentedReferenceProductResolvesToTheExpiredBaseOnTheDate() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(expiredPrice(), basePrice()), List.of(tenPercentDiscount()), date("2019-06-15"));

        assertThat(price).get().extracting(EffectivePrice::amount).isEqualTo(pln("249.00"));
    }

    @Test
    void e06Row2_activeBaseAndActiveDiscount_appliesTheDiscountOnce() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(tenPercentDiscount()), date("2019-07-15"));

        assertThat(price).get().satisfies(effective -> {
            assertThat(effective.amount()).isEqualTo(pln("233.10"));
            assertThat(effective.basePriceId()).isEqualTo("pr-2");
            assertThat(effective.discountId()).isEqualTo("pr-3");
        });
    }

    @Test
    void e06Row3_openEndedBaseAfterTheDiscountExpired_yieldsTheBaseAmount() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(tenPercentDiscount()), date("2019-08-01"));

        assertThat(price).get().satisfies(effective -> {
            assertThat(effective.amount()).isEqualTo(pln("259.00"));
            assertThat(effective.discountId()).isNull();
        });
    }

    @Test
    void e06Row4_validFromIsInclusive_discountAppliesOnItsFirstDay() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(tenPercentDiscount()), date("2019-07-01"));

        assertThat(price).get().extracting(EffectivePrice::amount).isEqualTo(pln("233.10"));
    }

    @Test
    void e06Row5_validToIsExclusive_discountStopsAtItsBoundary() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(tenPercentDiscount()), date("2019-08-01"));

        assertThat(price).get().satisfies(effective -> {
            assertThat(effective.amount()).isEqualTo(pln("259.00"));
            assertThat(effective.discountId()).isNull();
        });
    }

    @Test
    void e06Row6_noDiscountRow_yieldsTheBaseAmount() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(basePrice()), List.of(), date("2019-07-15"));

        assertThat(price).get().satisfies(effective -> {
            assertThat(effective.amount()).isEqualTo(pln("259.00"));
            assertThat(effective.discountId()).isNull();
        });
    }

    @Test
    void e06Row7_expiredBaseAppliesOnItsLastCoveredDay() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(expiredPrice()), List.of(), date("2019-06-29"));

        assertThat(price).get().satisfies(effective -> {
            assertThat(effective.amount()).isEqualTo(pln("249.00"));
            assertThat(effective.basePriceId()).isEqualTo("pr-1");
        });
    }

    @Test
    void e06Row7_expiredBaseDoesNotCoverItsExclusiveValidTo() {
        Optional<EffectivePrice> price = EffectivePrice.of(
                List.of(expiredPrice()), List.of(), date("2019-06-30"));

        assertThat(price).isEmpty();
    }

    @Test
    void e06Row8_overlappingDiscountsAreForbidden() {
        PriceSchedule schedule = PriceSchedule.forProduct(PricingFixture.PRODUCT_ID);
        schedule.scheduleDiscount("pr-3", Percent.of("10"), PricingFixture.between("2019-07-01", "2019-07-31"),
                PricingFixture.audit());

        assertThatExceptionOfType(PriceOverlap.class)
                .isThrownBy(() -> schedule.scheduleDiscount("pr-8", Percent.of("15"),
                        PricingFixture.between("2019-07-15", "2019-08-01"), PricingFixture.audit()));
    }

    @Test
    void e06Row2_resultCurrencyComesFromTheBase() {
        Price uahBase = new Price("pr-9", PricingFixture.PRODUCT_ID, Money.of("100.00", "UAH"),
                PricingFixture.openFrom("2019-07-01"));

        Optional<EffectivePrice> price = EffectivePrice.of(List.of(uahBase), List.of(tenPercentDiscount()),
                date("2019-07-15"));

        assertThat(price).get().extracting(EffectivePrice::amount).isEqualTo(Money.of("90.00", "UAH"));
    }
}
