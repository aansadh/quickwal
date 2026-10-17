package com.quickwal.wallet.core.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;

import static org.junit.jupiter.api.Assertions.*;

class WalletTest {

    private Wallet newWallet(long initialBalance) {
        return new Wallet(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(initialBalance, CurrencyType.QC)
        );
    }

    @Test
    void constructor_acceptsZeroInitialBalance() {
        Wallet wallet = newWallet(0L);

        assertEquals(new Money(0L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void constructor_rejectsNegativeInitialBalance() {
        assertThrows(
                IllegalArgumentException.class,
                () -> newWallet(-1L)
        );
    }

    @Test
    void debit_reducesBalance() {
        Wallet wallet = newWallet(1000L);

        wallet.debit(new Money(300L, CurrencyType.QC));

        assertEquals(new Money(700L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void debit_rejectsZeroAmount() {
        Wallet wallet = newWallet(1000L);

        assertThrows(
                IllegalArgumentException.class,
                () -> wallet.debit(new Money(0L, CurrencyType.QC))
        );

        assertEquals(new Money(1000L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void debit_rejectsNegativeAmount() {
        Wallet wallet = newWallet(1000L);

        assertThrows(
                IllegalArgumentException.class,
                () -> wallet.debit(new Money(-10L, CurrencyType.QC))
        );

        assertEquals(new Money(1000L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void debit_rejectsInsufficientFundsWithoutChangingBalance() {
        Wallet wallet = newWallet(100L);

        assertThrows(
                IllegalStateException.class,
                () -> wallet.debit(new Money(101L, CurrencyType.QC))
        );

        assertEquals(new Money(100L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void credit_increasesBalance() {
        Wallet wallet = newWallet(1000L);

        wallet.credit(new Money(300L, CurrencyType.QC));

        assertEquals(new Money(1300L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void credit_rejectsZeroAmount() {
        Wallet wallet = newWallet(1000L);

        assertThrows(
                IllegalArgumentException.class,
                () -> wallet.credit(new Money(0L, CurrencyType.QC))
        );

        assertEquals(new Money(1000L, CurrencyType.QC), wallet.getBalance());
    }

    @Test
    void credit_rejectsNegativeAmount() {
        Wallet wallet = newWallet(1000L);

        assertThrows(
                IllegalArgumentException.class,
                () -> wallet.credit(new Money(-10L, CurrencyType.QC))
        );

        assertEquals(new Money(1000L, CurrencyType.QC), wallet.getBalance());
    }
}
