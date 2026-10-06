package com.example.offer.pricing;

public record MoneyView(String value, String currency) {

    public static MoneyView of(Money money) {
        return new MoneyView(money.value().toPlainString(), money.currency().getCurrencyCode());
    }

    public Money toMoney() {
        return Money.of(value, currency);
    }
}
