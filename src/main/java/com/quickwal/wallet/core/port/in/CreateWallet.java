package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.UUID;

public interface CreateWallet {
    Wallet createWallet(UUID userId, long openingBalance);
}
