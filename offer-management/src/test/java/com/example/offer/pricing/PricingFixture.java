package com.example.offer.pricing;

import com.example.offer.auth.Audit;
import com.example.offer.auth.Identity;

import java.time.Instant;
import java.time.LocalDate;

final class PricingFixture {

    static final String PRODUCT_ID = "p-2019-0442";
    static final String PRICE_ID = "pr-2";
    static final String DISCOUNT_ID = "pr-3";
    static final Instant AT = Instant.parse("2019-06-20T11:00:00Z");
    static final Identity SALES = new Identity("s.zielinski");

    private PricingFixture() {
    }

    static Audit audit() {
        return new Audit(SALES, AT);
    }

    static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    static Money pln(String amount) {
        return Money.of(amount, "PLN");
    }

    static DateRange openFrom(String from) {
        return DateRange.from(date(from));
    }

    static DateRange between(String from, String to) {
        return new DateRange(date(from), date(to));
    }

    static Price price(String priceId, String amount, DateRange validity) {
        return new Price(priceId, PRODUCT_ID, pln(amount), validity);
    }

    static Discount discount(String discountId, String percent, DateRange validity) {
        return new Discount(discountId, PRODUCT_ID, Percent.of(percent), validity);
    }

    static Price basePrice() {
        return price(PRICE_ID, "259.00", openFrom("2019-07-01"));
    }

    static Discount tenPercentDiscount() {
        return discount(DISCOUNT_ID, "10", between("2019-07-01", "2019-07-31"));
    }

    static Price expiredPrice() {
        return price("pr-1", "249.00", between("2019-06-01", "2019-06-30"));
    }
}
