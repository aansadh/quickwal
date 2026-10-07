package com.quickwal.transfer.core.service;

import com.quickwal.ledger.core.port.in.LogTransfer;
import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.transfer.core.domain.CurrencyType;
import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.port.in.ExecuteTransfer;
import com.quickwal.transfer.core.port.out.TransferRepository;
import com.quickwal.wallet.core.domain.Wallet;
import com.quickwal.wallet.core.port.in.WalletUseCase;

import java.util.List;
import java.util.UUID;

public class TransferService implements ExecuteTransfer {

    private final WalletUseCase walletUseCase;
    private final LogTransfer logTransferUseCase;
    private final TransferRepository transferRepository;

    public TransferService(WalletUseCase walletUseCase,
                           LogTransfer logTransferUseCase,
                           TransferRepository transferRepository) {
        this.walletUseCase = walletUseCase;
        this.logTransferUseCase = logTransferUseCase;
        this.transferRepository = transferRepository;
    }

    @Override
    public Transfer execute(UUID idempotencyKey, UUID senderId, UUID receiverId,
                            long amount, CurrencyType currency, AcquisitionType acquisition) {

        Transfer transfer = Transfer.initiate(idempotencyKey)
                .fromSender(senderId)
                .toReceiver(receiverId)
                .withAmount(amount, currency)
                .viaAcquisition(acquisition)
                .build();

        Transfer idempotentSaveResult = transferRepository.saveIdempotently(transfer);
        if(!transfer.getId().equals(idempotentSaveResult.getId())) {
            return idempotentSaveResult;
        }

        // Database-level Lock Acquisition
        // The underlying adapter MUST implement: SELECT * FROM wallets WHERE id IN (?, ?) ORDER BY id FOR UPDATE
        List<Wallet> wallets;
        try {
            wallets = walletUseCase.lockAndGet(senderId, receiverId);
        } catch (IllegalArgumentException e) {
            transfer.markFailure("Wallet lookup failed: " + e.getMessage());
            transferRepository.update(transfer);
            return transfer;
        }

        Wallet sender = wallets.get(0).getId().equals(senderId) ? wallets.get(0) : wallets.get(1);
        Wallet receiver = wallets.get(0).getId().equals(receiverId) ? wallets.get(0) : wallets.get(1);

        try {
            sender.debit(amount);
            receiver.credit(amount);

            walletUseCase.update(sender);
            walletUseCase.update(receiver);

            logTransferUseCase.log(transfer.getId(), senderId, receiverId, amount);

            transfer.markSuccessful();
        } catch (Exception e) {
            transfer.markFailure(e.getMessage());
            throw e; // Rethrow to trigger Spring's @Transactional DB rollback, releasing DB locks
        } finally {
            transferRepository.update(transfer);
        }

        return transfer;
    }
}
