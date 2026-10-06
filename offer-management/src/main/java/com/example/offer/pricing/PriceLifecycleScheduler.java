package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;
import com.example.offer.publishing.IntegrationEvent.PriceView;
import com.example.offer.publishing.IntegrationEvent.ProductPricesChanged;
import com.example.offer.publishing.Outbox;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Emits {@code ProductPricesChanged} once per entry on the {@code SCHEDULED -> ACTIVE}
 * calendar transition (E08 T2, RULE-28). {@code ACTIVE -> EXPIRED} emits nothing (T3).
 * The business date comes from the single pinned clock (RULE-67); no aggregate holds a
 * clock — the date is passed into the domain (RULE-69).
 */
@Component
@RequiredArgsConstructor
class PriceLifecycleScheduler {

    private static final Identity SYSTEM = new Identity("system");

    private final PriceScheduleRepository repository;
    private final Outbox outbox;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${pricing.lifecycle.delay:PT1H}")
    void sweep() {
        sweep(LocalDate.now(clock));
    }

    void sweep(LocalDate businessDate) {
        Audit audit = new Audit(SYSTEM, clock.instant());
        for (PriceSchedule schedule : repository.all()) {
            String productId = schedule.toSnapshot().productId();
            for (PriceActivation activation : schedule.activationsAt(businessDate)) {
                Money amount = activation.amount();
                Percent discount = activation.discountPercent();
                outbox.append(new ProductPricesChanged(productId, businessDate,
                        amount == null ? null
                                : new PriceView(amount.value().toPlainString(),
                                        amount.currency().getCurrencyCode()),
                        discount == null ? null : discount.value().toPlainString(),
                        activation.entryId(), audit));
            }
        }
    }
}
