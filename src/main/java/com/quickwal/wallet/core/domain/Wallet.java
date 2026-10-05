package com.quickwal.wallet.core.domain;

import lombok.*;

@Getter @Setter
public class Wallet {
    private final Long id;
    private final Long userId;
    private long balance;

    public Wallet(Long id, Long userId, long initialBalance) {
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
