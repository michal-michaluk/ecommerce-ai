package com.example.offer.pricing;

import java.time.LocalDate;
import java.util.Objects;

public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from is required");
        if (to != null && to.isBefore(from)) {
            throw new InvalidDateRange(from, to);
        }
    }

    /** Open-ended range — validTo is null (RULE-23). */
    public static DateRange from(LocalDate from) {
        return new DateRange(from, null);
    }

    /** from inclusive, to exclusive (RULE-38); open-ended always covers. */
    public boolean covers(LocalDate date) {
        Objects.requireNonNull(date, "date is required");
        return !date.isBefore(from) && (to == null || date.isBefore(to));
    }
}
