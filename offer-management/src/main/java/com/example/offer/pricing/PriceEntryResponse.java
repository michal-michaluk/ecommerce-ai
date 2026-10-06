package com.example.offer.pricing;

import java.time.LocalDate;

/** Money is a decimal string + currency, never a float (element 02). */
public record PriceEntryResponse(String priceId, String kind, MoneyView amount, String percent,
                                 LocalDate validFrom, LocalDate validTo, String state) {

    public static PriceEntryResponse of(Price price, LocalDate businessDate) {
        return new PriceEntryResponse(price.priceId(), PriceKind.PRICE.name(), MoneyView.of(price.amount()),
                null, price.validity().from(), price.validity().to(), price.stateAt(businessDate).name());
    }

    public static PriceEntryResponse of(Discount discount, LocalDate businessDate) {
        return new PriceEntryResponse(discount.discountId(), PriceKind.DISCOUNT.name(), null,
                discount.percent().value().toPlainString(), discount.validity().from(),
                discount.validity().to(), discount.stateAt(businessDate).name());
    }
}
