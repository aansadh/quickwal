package com.quickwal.wallet.core.service;

import com.quickwal.common.messages.command.*;
import com.quickwal.common.messages.event.*;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.wallet.core.port.in.ProcessWalletCommand;
import com.quickwal.wallet.core.port.out.WalletRepository;

/**
 * Provides the WalletService class used by QuickWal.
 */
public class WalletService implements ProcessWalletCommand {
    private final WalletRepository walletRepository;

    /**
     * Creates a WalletService.
     * @param walletRepository input value
     */
    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

        /**
     * Processes a debit command.
     * @param command input value
     */

    @Override
    public void debit(DebitCommand command) {
        try {
            DebitCompletedEvent successEvent = new DebitCompletedEvent(
                    IdGenerator.generate(),
                    command.transferId(),
                    command.sourceWalletId()
            );

            walletRepository.deductAndSaveOutboxEvent(
                    command.sourceWalletId(),
                    command.amount(),
                    command.eventId(),
                    successEvent
            );
        } catch (Exception e) {
            DebitFailedEvent failureEvent = new DebitFailedEvent(
                    IdGenerator.generate(),
                    command.transferId(),
                    command.sourceWalletId(),
                    e.getMessage()
            );
            walletRepository.saveFailureOutboxEvent(command.sourceWalletId(), failureEvent);
        }
    }

        /**
     * Processes a credit command.
     * @param command input value
     */

    @Override
    public void credit(CreditCommand command) {
        try {
            CreditCompletedEvent successEvent = new CreditCompletedEvent(
                    IdGenerator.generate(),
                    command.transferId(),
                    command.targetWalletId()
            );

            walletRepository.addAndSaveOutboxEvent(
                    command.targetWalletId(),
                    command.amount(),
                    command.eventId(),
                    successEvent
            );
        } catch (Exception e) {
            CreditFailedEvent failureEvent = new CreditFailedEvent(
                    IdGenerator.generate(),
                    command.transferId(),
                    command.targetWalletId(),
                    e.getMessage()
            );
            walletRepository.saveFailureOutboxEvent(command.targetWalletId(), failureEvent);
        }
    }

        /**
     * Processes a refund command.
     * @param command input value
     */

    @Override
    public void refund(RefundCommand command) {
        try {
            RefundCompletedEvent successEvent = new RefundCompletedEvent(
                    IdGenerator.generate(),
                    command.transferId(),
                    command.sourceWalletId()
            );

            walletRepository.refundAndSaveOutboxEvent(
                    command.sourceWalletId(),
                    command.amount(),
                    command.eventId(),
                    successEvent
            );
        } catch (Exception e) {
            throw new RuntimeException("CRITICAL: Failed to process refund for transfer " + command.transferId(), e);
        }
    }
}
