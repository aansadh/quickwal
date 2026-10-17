package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;
import com.quickwal.common.money.Money;

import java.util.UUID;

/**
 * Defines the CreateWallet interface used by QuickWal.
 */
public interface CreateWallet {
    /**
     * Creates a wallet.
     * @param userId input value
     * @param openingBalance input value
     * @return the operation result
     */
    Wallet createWallet(UUID userId, Money openingBalance);
}
