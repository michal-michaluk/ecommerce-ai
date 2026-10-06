package com.example.offer.pricing;

import java.math.BigDecimal;
import java.util.Objects;

public record Percent(BigDecimal value) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public Percent {
        Objects.requireNonNull(value, "value is required");
        if (value.signum() <= 0 || value.compareTo(HUNDRED) >= 0) {
            throw new IllegalArgumentException("percent must be in (0, 100)");
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException("percent scale must be at most 2");
        }
    }

    public static Percent of(String value) {
        return new Percent(new BigDecimal(value));
    }
}
