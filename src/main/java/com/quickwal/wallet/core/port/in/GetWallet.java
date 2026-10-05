package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.Optional;

public interface GetWallet {
    Wallet getWallet(Long walletId);
}
