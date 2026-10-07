package com.quickwal.wallet.core.port.in;

import com.quickwal.wallet.core.domain.Wallet;

import java.util.List;
import java.util.UUID;

public interface WalletUseCase {
    List<Wallet> lockAndGet(UUID senderId, UUID receiverId);
    void update(Wallet wallet);
}
