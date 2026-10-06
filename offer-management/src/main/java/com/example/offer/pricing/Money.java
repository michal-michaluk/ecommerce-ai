package com.example.offer.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

public record Money(BigDecimal value, Currency currency) {

    public Money {
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(currency, "currency is required");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("money must not be negative");
        }
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount).setScale(2, RoundingMode.HALF_UP),
                Currency.getInstance(currencyCode));
    }

    /** Applies a discount and rounds once, at the end — RULE-46. */
    public Money discountedBy(Percent percent) {
        BigDecimal factor = BigDecimal.ONE.subtract(percent.value().movePointLeft(2));
        return new Money(value.multiply(factor).setScale(2, RoundingMode.HALF_UP), currency);
    }
}
