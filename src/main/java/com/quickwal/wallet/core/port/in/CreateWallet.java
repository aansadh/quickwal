package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;

public interface CreateWallet {
    Wallet createWallet(Long userId, long openingBalance);
}
