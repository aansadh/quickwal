package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.UUID;

/**
 * Defines the GetWallet interface used by QuickWal.
 */
public interface GetWallet {
    /**
     * Retrieves a wallet.
     * @param walletId input value
     * @return the operation result
     */
    Wallet getWallet(UUID walletId);
}
