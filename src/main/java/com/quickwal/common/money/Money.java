package com.quickwal.common.money;

import java.util.Objects;

/** Immutable monetary value represented in integer minor units. */
public record Money(long amountMinor, CurrencyType currency) {
    public Money {
        Objects.requireNonNull(currency, "currency");
    }
    public boolean isPositive() { return amountMinor > 0; }
    public boolean isZero() { return amountMinor == 0; }
    public boolean isNegative() { return amountMinor < 0; }
    public Money requirePositive() {
        if (!isPositive()) throw new IllegalArgumentException("Minor units must be positive");
        return this;
    }
    public Money add(Money other) {
        requireCurrency(other);
        return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
    }
    public Money subtract(Money other) {
        requireCurrency(other);
        return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
    }
    private void requireCurrency(Money other) {
        if (other == null || currency != other.currency) throw new IllegalArgumentException("Currency mismatch");
    }
}
