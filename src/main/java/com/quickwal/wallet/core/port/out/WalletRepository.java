package com.quickwal.wallet.core.port.out;

import com.quickwal.common.money.Money;
import java.util.UUID;

/** Persistence operations for wallet balances. */
public interface WalletRepository {
    void debit(UUID walletId, Money amount);
    void credit(UUID walletId, Money amount);
    void refund(UUID walletId, Money amount);
}
