package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.example.offer.pricing.PricingFixture.PRODUCT_ID;
import static com.example.offer.pricing.PricingFixture.audit;
import static com.example.offer.pricing.PricingFixture.between;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.openFrom;
import static com.example.offer.pricing.PricingFixture.pln;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PriceScheduleTest {

    private final PriceSchedule schedule = PriceSchedule.forProduct(PRODUCT_ID);
    private final LocalDate businessDate = date("2019-06-20");

    @Test
    void rejectsOverlappingPrices() {
        schedule.schedulePrice("pr-1", pln("100.00"), between("2019-06-01", "2019-06-30"), audit());

        assertThatExceptionOfType(PriceOverlap.class).isThrownBy(() ->
                schedule.schedulePrice("pr-2", pln("200.00"), between("2019-06-15", "2019-07-01"), audit()));
    }

    @Test
    void acceptsAdjacentPriceRangesBecauseValidToIsExclusive() {
        schedule.schedulePrice("pr-1", pln("100.00"), between("2019-06-01", "2019-06-30"), audit());
        schedule.schedulePrice("pr-2", pln("200.00"), between("2019-06-30", "2019-07-31"), audit());

        assertThat(schedule.toSnapshot().prices()).hasSize(2);
    }

    @Test
    void rejectsOverlappingDiscounts() {
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());

        assertThatExceptionOfType(PriceOverlap.class).isThrownBy(() ->
                schedule.scheduleDiscount("pr-4", Percent.of("15"), between("2019-07-15", "2019-08-01"), audit()));
    }

    @Test
    void schedulingAPriceEmitsPricesChangedFromItsValidFrom() {
        schedule.schedulePrice("pr-2", pln("259.00"), openFrom("2019-07-01"), audit());

        assertThat(schedule.events).last().isEqualTo(
                new DomainEvent.ProductPricesChanged(PRODUCT_ID, date("2019-07-01"), audit()));
    }

    @Test
    void editsAScheduledPriceInPlace() {
        schedule.schedulePrice("pr-2", pln("259.00"), between("2019-08-01", "2019-09-01"), audit());

        schedule.changePrice("pr-2", pln("269.00"), between("2019-08-01", "2019-09-01"), businessDate, audit());

        List<Price> prices = schedule.toSnapshot().prices();
        assertThat(prices).hasSize(1);
        assertThat(prices.get(0).priceId()).isEqualTo("pr-2");
        assertThat(prices.get(0).amount()).isEqualTo(pln("269.00"));
    }

    @Test
    void rejectsChangingAnActivePrice() {
        schedule.schedulePrice("pr-2", pln("259.00"), between("2019-06-01", "2019-07-01"), audit());

        assertThatExceptionOfType(EntryNotEditable.class).isThrownBy(() ->
                schedule.changePrice("pr-2", pln("269.00"), between("2019-06-01", "2019-07-01"),
                        businessDate, audit()));
    }

    @Test
    void rejectsChangingAnExpiredPrice() {
        schedule.schedulePrice("pr-1", pln("249.00"), between("2019-05-01", "2019-06-01"), audit());

        assertThatExceptionOfType(EntryNotEditable.class).isThrownBy(() ->
                schedule.changePrice("pr-1", pln("269.00"), between("2019-05-01", "2019-06-01"),
                        businessDate, audit()));
    }

    @Test
    void deletesAScheduledEntry() {
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-08-01", "2019-09-01"), audit());

        schedule.deleteDiscount("pr-3", businessDate, audit());

        assertThat(schedule.toSnapshot().discounts()).isEmpty();
    }

    @Test
    void rejectsDeletingAnActiveEntryAndKeepsItRetained() {
        schedule.schedulePrice("pr-2", pln("259.00"), between("2019-06-01", "2019-07-01"), audit());

        assertThatExceptionOfType(EntryNotEditable.class).isThrownBy(() ->
                schedule.deletePrice("pr-2", businessDate, audit()));

        assertThat(schedule.toSnapshot().prices()).hasSize(1);
    }

    @Test
    void resolvesTheEffectivePriceAtItsOwnBusinessDate() {
        schedule.schedulePrice("pr-2", pln("259.00"), openFrom("2019-07-01"), audit());
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());

        assertThat(schedule.effectivePriceAt(date("2019-07-15"))).get()
                .extracting(EffectivePrice::amount).isEqualTo(pln("233.10"));
        assertThat(schedule.effectivePriceAt(date("2019-06-15"))).isEmpty();
    }
}
