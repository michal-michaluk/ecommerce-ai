package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@AllArgsConstructor
class PriceSchedule {

    private final String productId;
    final List<DomainEvent> events;
    private List<Price> prices;
    private List<Discount> discounts;

    static PriceSchedule forProduct(String productId) {
        return new PriceSchedule(productId, new ArrayList<>(), List.of(), List.of());
    }

    void schedulePrice(String priceId, Money amount, DateRange validity, Audit audit) {
        checkNoOverlap(prices.stream().map(Price::validity).toList(), validity);
        this.prices = append(prices, new Price(priceId, productId, amount, validity));
        events.add(new DomainEvent.ProductPricesChanged(productId, validity.from(), audit));
    }

    void changePrice(String priceId, Money amount, DateRange validity, LocalDate businessDate, Audit audit) {
        Price current = priceById(priceId);
        checkEditable(priceId, current.editableAt(businessDate));
        checkNoOverlap(otherPriceRanges(priceId), validity);
        this.prices = prices.stream()
                .map(price -> price.priceId().equals(priceId)
                        ? new Price(priceId, productId, amount, validity)
                        : price)
                .toList();
        events.add(new DomainEvent.ProductPricesChanged(productId, validity.from(), audit));
    }

    void deletePrice(String priceId, LocalDate businessDate, Audit audit) {
        Price current = priceById(priceId);
        checkEditable(priceId, current.editableAt(businessDate));
        this.prices = prices.stream().filter(price -> !price.priceId().equals(priceId)).toList();
        events.add(new DomainEvent.ProductPricesChanged(productId, current.validity().from(), audit));
    }

    void scheduleDiscount(String discountId, Percent percent, DateRange validity, Audit audit) {
        checkNoOverlap(discounts.stream().map(Discount::validity).toList(), validity);
        this.discounts = append(discounts, new Discount(discountId, productId, percent, validity));
        events.add(new DomainEvent.ProductPricesChanged(productId, validity.from(), audit));
    }

    void changeDiscount(String discountId, Percent percent, DateRange validity, LocalDate businessDate, Audit audit) {
        Discount current = discountById(discountId);
        checkEditable(discountId, current.editableAt(businessDate));
        checkNoOverlap(otherDiscountRanges(discountId), validity);
        this.discounts = discounts.stream()
                .map(discount -> discount.discountId().equals(discountId)
                        ? new Discount(discountId, productId, percent, validity)
                        : discount)
                .toList();
        events.add(new DomainEvent.ProductPricesChanged(productId, validity.from(), audit));
    }

    void deleteDiscount(String discountId, LocalDate businessDate, Audit audit) {
        Discount current = discountById(discountId);
        checkEditable(discountId, current.editableAt(businessDate));
        this.discounts = discounts.stream().filter(discount -> !discount.discountId().equals(discountId)).toList();
        events.add(new DomainEvent.ProductPricesChanged(productId, current.validity().from(), audit));
    }

    /** Resolved here and at read time — never a field on the draft (RULE-68). */
    Optional<EffectivePrice> effectivePriceAt(LocalDate businessDate) {
        return EffectivePrice.of(prices, discounts, businessDate);
    }

    /**
     * Entries that cross from {@code SCHEDULED} to {@code ACTIVE} on {@code businessDate}
     * (E08 T2). {@code ACTIVE} to {@code EXPIRED} (T3) is deliberately not returned: it emits
     * nothing. Each crossing carries the resolved view the shop needs (element 03).
     */
    List<PriceActivation> activationsAt(LocalDate businessDate) {
        LocalDate previous = businessDate.minusDays(1);
        List<PriceActivation> activations = new ArrayList<>();
        for (Price price : prices) {
            if (crossesIntoActive(price.stateAt(previous), price.stateAt(businessDate))) {
                activations.add(new PriceActivation(price.priceId(), price.amount(), activeDiscount(businessDate)));
            }
        }
        for (Discount discount : discounts) {
            if (crossesIntoActive(discount.stateAt(previous), discount.stateAt(businessDate))) {
                Money base = activePrice(businessDate).map(Price::amount).orElse(null);
                activations.add(new PriceActivation(discount.discountId(), base, discount.percent()));
            }
        }
        return activations;
    }

    private static boolean crossesIntoActive(PriceState before, PriceState after) {
        return before == PriceState.SCHEDULED && after == PriceState.ACTIVE;
    }

    private Optional<Price> activePrice(LocalDate businessDate) {
        return prices.stream().filter(price -> price.stateAt(businessDate) == PriceState.ACTIVE).findFirst();
    }

    private Percent activeDiscount(LocalDate businessDate) {
        return discounts.stream()
                .filter(discount -> discount.stateAt(businessDate) == PriceState.ACTIVE)
                .map(Discount::percent)
                .findFirst()
                .orElse(null);
    }

    PriceScheduleSnapshot toSnapshot() {
        return new PriceScheduleSnapshot(productId, prices, discounts);
    }

    private void checkNoOverlap(List<DateRange> existing, DateRange candidate) {
        if (existing.stream().anyMatch(range -> overlaps(range, candidate))) {
            throw new PriceOverlap(productId, candidate);
        }
    }

    private static boolean overlaps(DateRange one, DateRange two) {
        LocalDate oneTo = one.to() == null ? LocalDate.MAX : one.to();
        LocalDate twoTo = two.to() == null ? LocalDate.MAX : two.to();
        return one.from().isBefore(twoTo) && two.from().isBefore(oneTo);
    }

    private Price priceById(String priceId) {
        return prices.stream().filter(price -> price.priceId().equals(priceId)).findFirst()
                .orElseThrow(() -> new EntryNotEditable(priceId));
    }

    private Discount discountById(String discountId) {
        return discounts.stream().filter(discount -> discount.discountId().equals(discountId)).findFirst()
                .orElseThrow(() -> new EntryNotEditable(discountId));
    }

    private List<DateRange> otherPriceRanges(String priceId) {
        return prices.stream().filter(price -> !price.priceId().equals(priceId)).map(Price::validity).toList();
    }

    private List<DateRange> otherDiscountRanges(String discountId) {
        return discounts.stream()
                .filter(discount -> !discount.discountId().equals(discountId))
                .map(Discount::validity)
                .toList();
    }

    private static void checkEditable(String entryId, boolean editable) {
        if (!editable) {
            throw new EntryNotEditable(entryId);
        }
    }

    private static <T> List<T> append(List<T> entries, T entry) {
        return Stream.concat(entries.stream(), Stream.of(entry)).toList();
    }
}
