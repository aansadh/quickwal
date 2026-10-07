package com.quickwal.wallet.core.domain;

import lombok.*;

import java.util.UUID;

@Getter @Setter
public class Wallet {
    private final UUID id;
    private final UUID userId;
    private long balance;

    public Wallet(UUID id, UUID userId, long initialBalance) {
        if (initialBalance < 0) {
            throw new IllegalArgumentException("Initial balance cannot be negative");
        }
        this.id = id;
        this.userId = userId;
        this.balance = initialBalance;
    }

    public void debit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (this.balance < amount) throw new IllegalStateException("Insufficient funds");
        this.balance -= amount;
    }

    public void credit(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        this.balance += amount;
    }
}
