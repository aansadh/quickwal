package com.quickwal.wallet.core.service;

import com.quickwal.wallet.core.domain.Wallet;
import com.quickwal.wallet.core.port.in.CreateWallet;
import com.quickwal.wallet.core.port.in.GetWallet;
import com.quickwal.wallet.core.port.out.WalletRepository;
import com.quickwal.common.utils.IdGenerator;

import java.util.UUID;

public class WalletService implements CreateWallet, GetWallet {
    private final WalletRepository walletRepository;
    private final IdGenerator idGenerator;

    public WalletService(WalletRepository walletRepository, IdGenerator idGenerator) {
        this.walletRepository = walletRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    public Wallet createWallet(UUID userId, long openingBalance) {
        UUID id = idGenerator.generate();
        Wallet wallet = new Wallet(id, userId, openingBalance);
        walletRepository.save(wallet);
        return wallet;
    }

    @Override
    public Wallet getWallet(UUID walletId) throws IllegalArgumentException {
        return walletRepository.findById(walletId).orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }
}
