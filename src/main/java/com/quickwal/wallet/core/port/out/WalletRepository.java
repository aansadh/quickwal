package com.quickwal.wallet.core.port.out;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.Optional;

public interface WalletRepository {
    Optional<Wallet> findById(Long walletId);
    void save(Wallet wallet);
}
