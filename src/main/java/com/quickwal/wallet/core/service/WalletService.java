package com.quickwal.wallet.core.service;

import com.quickwal.wallet.core.domain.Wallet;
import com.quickwal.wallet.core.port.in.CreateWallet;
import com.quickwal.wallet.core.port.in.GetWallet;
import com.quickwal.wallet.core.port.in.WalletUseCase;
import com.quickwal.wallet.core.port.out.WalletRepository;
import com.quickwal.common.utils.IdGenerator;

import java.util.List;
import java.util.UUID;

public class WalletService implements CreateWallet, GetWallet, WalletUseCase {
    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Override
    public Wallet createWallet(UUID userId, long openingBalance) {
        UUID id = IdGenerator.generate();
        Wallet wallet = new Wallet(id, userId, openingBalance);
        walletRepository.save(wallet);
        return wallet;
    }

    @Override
    public Wallet getWallet(UUID walletId) throws IllegalArgumentException {
        return walletRepository.findById(walletId).orElseThrow(() -> new IllegalArgumentException("Wallet not found"));
    }

    @Override
    public List<Wallet> lockAndGet(UUID senderId, UUID receiverId) {
        return walletRepository.getWalletsForUpdate(senderId, receiverId);
    }

    @Override
    public void update(Wallet wallet) {
        walletRepository.update(wallet);
    }
}
