package com.example.offer.offer;

import com.example.offer.pricing.PriceScheduleSnapshot;
import com.example.offer.pricing.PriceState;

import java.time.LocalDate;

/** The price fact behind PRICE_REQUIRED, resolved from the current price state (E05). */
enum PricePresence {
    ACTIVE, SCHEDULED, EXPIRED, NONE;

    boolean present() {
        return this == ACTIVE || this == SCHEDULED;
    }

    static PricePresence of(PriceScheduleSnapshot schedule, LocalDate businessDate) {
        if (schedule == null || schedule.prices().isEmpty()) {
            return NONE;
        }
        return schedule.prices().stream()
                .map(price -> price.stateAt(businessDate))
                .filter(state -> state != PriceState.EXPIRED)
                .findFirst()
                .map(state -> state == PriceState.ACTIVE ? ACTIVE : SCHEDULED)
                .orElse(EXPIRED);
    }
}
