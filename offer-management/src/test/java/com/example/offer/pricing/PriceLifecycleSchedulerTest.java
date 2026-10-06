package com.example.offer.pricing;

import com.example.offer.publishing.IntegrationEvent;
import com.example.offer.publishing.Outbox;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.example.offer.pricing.PricingFixture.PRODUCT_ID;
import static com.example.offer.pricing.PricingFixture.audit;
import static com.example.offer.pricing.PricingFixture.between;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.openFrom;
import static com.example.offer.pricing.PricingFixture.pln;
import static org.assertj.core.api.Assertions.assertThat;

class PriceLifecycleSchedulerTest {

    private final InMemorySchedules schedules = new InMemorySchedules();
    private final RecordingOutbox outbox = new RecordingOutbox();
    private final Clock clock = Clock.fixed(Instant.parse("2019-06-20T11:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private final PriceLifecycleScheduler scheduler = new PriceLifecycleScheduler(schedules, outbox, clock);

    @Test
    void aScheduledEntryCrossingItsValidFromEmitsExactlyOnce() {
        schedules.save(scheduleWith(price("pr-2", "259.00", openFrom("2019-07-01"))));

        scheduler.sweep(date("2019-07-01"));

        assertThat(outbox.events).hasSize(1);
        assertThat(outbox.events.get(0)).isInstanceOf(IntegrationEvent.ProductPricesChanged.class);
        var event = (IntegrationEvent.ProductPricesChanged) outbox.events.get(0);
        assertThat(event.productId()).isEqualTo(PRODUCT_ID);
        assertThat(event.effectiveFrom()).isEqualTo(date("2019-07-01"));
        assertThat(event.price().value()).isEqualTo("259.00");
        assertThat(event.price().currency()).isEqualTo("PLN");
        assertThat(event.discountPercent()).isNull();
        assertThat(event.entryId()).isEqualTo("pr-2");
        assertThat(event.audit()).isEqualTo(new com.example.offer.auth.Audit(
                new com.example.offer.auth.Identity("system"), clock.instant()));
    }

    @Test
    void aSecondRunDoesNotReEmitTheSameTransition() {
        schedules.save(scheduleWith(price("pr-2", "259.00", openFrom("2019-07-01"))));

        scheduler.sweep(date("2019-07-01"));
        scheduler.sweep(date("2019-07-01"));

        assertThat(outbox.events).hasSize(1);
    }

    @Test
    void anActiveToExpiredCrossingEmitsNothing() {
        schedules.save(scheduleWith(price("pr-1", "249.00", between("2019-06-01", "2019-06-30"))));

        scheduler.sweep(date("2019-06-30"));

        assertThat(outbox.events).isEmpty();
    }

    @Test
    void entriesThatDoNotCrossEmitNothing() {
        schedules.save(scheduleWith(price("pr-2", "259.00", openFrom("2019-07-01"))));

        scheduler.sweep(date("2019-06-30"));

        assertThat(outbox.events).isEmpty();
    }

    @Test
    void anEntryAlreadyActiveDoesNotReEmit() {
        schedules.save(scheduleWith(price("pr-2", "259.00", openFrom("2019-07-01"))));

        scheduler.sweep(date("2019-07-05"));

        assertThat(outbox.events).isEmpty();
    }

    @Test
    void aDiscountCrossingEmitsTheResolvedBasePriceAndPercent() {
        PriceSchedule schedule = scheduleWith(price("pr-2", "259.00", openFrom("2019-07-01")));
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());
        schedules.save(schedule);

        scheduler.sweep(date("2019-07-01"));

        assertThat(outbox.events).hasSize(2);
        assertThat(outbox.events)
                .extracting(event -> ((IntegrationEvent.ProductPricesChanged) event).price().value())
                .containsOnly("259.00");
        assertThat(outbox.events)
                .extracting(event -> ((IntegrationEvent.ProductPricesChanged) event).entryId())
                .containsExactlyInAnyOrder("pr-2", "pr-3");
        assertThat(outbox.events)
                .extracting(event -> ((IntegrationEvent.ProductPricesChanged) event).discountPercent())
                .containsOnly("10");
    }

    @Test
    void aDiscountCrossingWithoutABasePriceStillEmitsOnceWithNoPrice() {
        PriceSchedule schedule = PriceSchedule.forProduct(PRODUCT_ID);
        schedule.scheduleDiscount("pr-3", Percent.of("10"), between("2019-07-01", "2019-07-31"), audit());
        schedules.save(schedule);

        scheduler.sweep(date("2019-07-01"));

        assertThat(outbox.events).hasSize(1);
        var event = (IntegrationEvent.ProductPricesChanged) outbox.events.get(0);
        assertThat(event.entryId()).isEqualTo("pr-3");
        assertThat(event.price()).isNull();
        assertThat(event.discountPercent()).isEqualTo("10");
    }

    private static PriceSchedule scheduleWith(Price price) {
        PriceSchedule schedule = PriceSchedule.forProduct(PRODUCT_ID);
        schedule.schedulePrice(price.priceId(), price.amount(), price.validity(), audit());
        return schedule;
    }

    private static Price price(String priceId, String amount, DateRange validity) {
        return new Price(priceId, PRODUCT_ID, pln(amount), validity);
    }

    static class InMemorySchedules implements PriceScheduleRepository {

        private final Map<String, PriceSchedule> schedules = new LinkedHashMap<>();

        @Override
        public Optional<PriceSchedule> get(String productId) {
            return Optional.ofNullable(schedules.get(productId));
        }

        @Override
        public List<PriceSchedule> all() {
            return List.copyOf(schedules.values());
        }

        @Override
        public void save(PriceSchedule schedule) {
            schedules.put(schedule.toSnapshot().productId(), schedule);
        }
    }

    static class RecordingOutbox implements Outbox {

        final List<IntegrationEvent> events = new ArrayList<>();
        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean append(IntegrationEvent event) {
            if (!keys.add(event.outboxKey())) {
                return false;
            }
            events.add(event);
            return true;
        }
    }
}
