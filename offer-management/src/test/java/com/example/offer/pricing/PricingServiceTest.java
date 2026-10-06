package com.example.offer.pricing;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.example.offer.pricing.PricingFixture.PRODUCT_ID;
import static com.example.offer.pricing.PricingFixture.audit;
import static com.example.offer.pricing.PricingFixture.between;
import static com.example.offer.pricing.PricingFixture.date;
import static com.example.offer.pricing.PricingFixture.openFrom;
import static com.example.offer.pricing.PricingFixture.pln;
import static org.assertj.core.api.Assertions.assertThat;

class PricingServiceTest {

    private final Map<String, PriceSchedule> store = new HashMap<>();
    private final PricingService service = new PricingService(new FakeRepository());

    @Test
    void schedulesAPriceForANewProduct() {
        PriceScheduleSnapshot snapshot = service.schedulePrice(PRODUCT_ID, "pr-2", pln("259.00"),
                openFrom("2019-07-01"), audit());

        assertThat(snapshot.prices()).hasSize(1);
        assertThat(service.get(PRODUCT_ID).orElseThrow().prices()).hasSize(1);
    }

    @Test
    void editsAScheduledPriceAcrossCalls() {
        LocalDate businessDate = date("2019-06-20");
        service.schedulePrice(PRODUCT_ID, "pr-2", pln("259.00"), between("2019-08-01", "2019-09-01"), audit());

        service.changePrice(PRODUCT_ID, "pr-2", pln("269.00"), between("2019-08-01", "2019-09-01"),
                businessDate, audit());

        assertThat(service.get(PRODUCT_ID).orElseThrow().prices().get(0).amount()).isEqualTo(pln("269.00"));
    }

    @Test
    void missingProductHasNoSchedule() {
        assertThat(service.get("missing")).isEmpty();
        assertThat(service.effectivePriceAt("missing", date("2019-07-15"))).isEmpty();
    }

    private final class FakeRepository implements PriceScheduleRepository {

        @Override
        public Optional<PriceSchedule> get(String productId) {
            return Optional.ofNullable(store.get(productId));
        }

        @Override
        public List<PriceSchedule> all() {
            return List.copyOf(store.values());
        }

        @Override
        public void save(PriceSchedule schedule) {
            store.put(schedule.toSnapshot().productId(), schedule);
        }
    }
}
