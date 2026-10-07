package com.quickwal.wallet.core.port.out;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository {
    Optional<Wallet> findById(UUID walletId);
    void save(Wallet wallet);
}
