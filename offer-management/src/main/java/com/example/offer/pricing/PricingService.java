package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Transactional
@RequiredArgsConstructor
public class PricingService {

    private final PriceScheduleRepository repository;

    public Optional<PriceScheduleSnapshot> get(String productId) {
        return repository.get(productId).map(PriceSchedule::toSnapshot);
    }

    public Optional<EffectivePrice> effectivePriceAt(String productId, LocalDate businessDate) {
        return repository.get(productId).flatMap(schedule -> schedule.effectivePriceAt(businessDate));
    }

    public PriceScheduleSnapshot schedulePrice(String productId, String priceId, Money amount,
                                               DateRange validity, Audit audit) {
        return mutate(productId, schedule -> schedule.schedulePrice(priceId, amount, validity, audit));
    }

    public PriceScheduleSnapshot changePrice(String productId, String priceId, Money amount,
                                             DateRange validity, LocalDate businessDate, Audit audit) {
        return mutate(productId, schedule -> schedule.changePrice(priceId, amount, validity, businessDate, audit));
    }

    public PriceScheduleSnapshot deletePrice(String productId, String priceId, LocalDate businessDate, Audit audit) {
        return mutate(productId, schedule -> schedule.deletePrice(priceId, businessDate, audit));
    }

    public PriceScheduleSnapshot scheduleDiscount(String productId, String discountId, Percent percent,
                                                  DateRange validity, Audit audit) {
        return mutate(productId, schedule -> schedule.scheduleDiscount(discountId, percent, validity, audit));
    }

    public PriceScheduleSnapshot changeDiscount(String productId, String discountId, Percent percent,
                                                DateRange validity, LocalDate businessDate, Audit audit) {
        return mutate(productId,
                schedule -> schedule.changeDiscount(discountId, percent, validity, businessDate, audit));
    }

    public PriceScheduleSnapshot deleteDiscount(String productId, String discountId, LocalDate businessDate,
                                                Audit audit) {
        return mutate(productId, schedule -> schedule.deleteDiscount(discountId, businessDate, audit));
    }

    private PriceScheduleSnapshot mutate(String productId, Consumer<PriceSchedule> operation) {
        PriceSchedule schedule = repository.get(productId).orElseGet(() -> PriceSchedule.forProduct(productId));
        operation.accept(schedule);
        repository.save(schedule);
        return schedule.toSnapshot();
    }
}
