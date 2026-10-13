package com.quickwal.wallet.core.domain;

import lombok.*;

import java.util.UUID;

@Getter @Setter
/**
 * Provides the Wallet class used by QuickWal.
 */
public class Wallet {
    private final UUID id;
    private final UUID userId;
    private long balance;

    /**
     * Creates a Wallet.
     * @param id input value
     * @param userId input value
     * @param initialBalance input value
     */
    public Wallet(UUID id, UUID userId, long initialBalance) {
        if (initialBalance < 0) {

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
    public void debit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (this.balance < amount) throw new IllegalStateException("Insufficient funds");
        this.balance -= amount;
    }

    /**
     * Processes a credit command.
     * @param amount input value
     */
    public void credit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balance += amount;
    }
}
