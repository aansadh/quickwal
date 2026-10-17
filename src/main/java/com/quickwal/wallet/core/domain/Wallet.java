package com.quickwal.wallet.core.domain;

import lombok.Getter;

import java.util.UUID;
import com.quickwal.common.money.Money;

@Getter
/**
 * Provides the Wallet class used by QuickWal.
 */
public class Wallet {
    private final UUID id;
    private final UUID userId;
    private Money balance;

    /**
     * Creates a Wallet.
     * @param id input value
     * @param userId input value
     * @param initialBalance input value
     */
    public Wallet(UUID id, UUID userId, Money initialBalance) {
        initialBalance = java.util.Objects.requireNonNull(initialBalance, "initialBalance");
        if (initialBalance.isNegative()) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        this.id = id;
        this.userId = userId;
        this.balance = initialBalance;
    }

    /**
     * Processes a debit command.
     * @param amount input value
     */
    public void debit(Money amount) {
        amount.requirePositive();
        requireCurrency(amount);
        if (this.balance.amountMinor() < amount.amountMinor()) throw new IllegalStateException("Insufficient funds");
        this.balance = balance.subtract(amount);
    }

    /**
     * Processes a credit command.
     * @param amount input value
     */
    public void credit(Money amount) {
        amount.requirePositive();
        requireCurrency(amount);
        this.balance = balance.add(amount);
    }

    public Money getBalance() { return balance; }
    private void requireCurrency(Money amount) {
        if (balance.currency() != amount.currency()) throw new IllegalArgumentException("Currency mismatch");
    }
}
