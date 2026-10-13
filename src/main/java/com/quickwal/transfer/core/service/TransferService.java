package com.quickwal.transfer.core.service;

import com.quickwal.common.messages.command.CreditCommand;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.command.RefundCommand;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.transfer.core.domain.*;
import com.quickwal.transfer.core.domain.exception.TransferNotFoundException;
import com.quickwal.transfer.core.port.in.AdvanceTransferSaga;
import com.quickwal.transfer.core.port.in.InitiateTransfer;
import com.quickwal.transfer.core.port.out.TransferRepository;

import java.util.UUID;

/**
 * Provides the TransferService class used by QuickWal.
 */
public class TransferService implements InitiateTransfer, AdvanceTransferSaga {
    private final TransferRepository transferRepository;

    /**
     * Creates a TransferService.
     * @param transferRepository input value
     */
    public TransferService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

        /**
     * Initiates a transfer.
     * @param sourceWalletId input value
     * @param targetWalletId input value
     * @param amount input value
     * @param idempotencyKey input value
     * @return the operation result
     */

    @Override
    public UUID execute(UUID sourceWalletId, UUID targetWalletId, long amount, String idempotencyKey) {
        UUID idempotencyId = UUID.fromString(idempotencyKey);
        Transfer transfer = Transfer.initiate(idempotencyId)
                .fromSender(sourceWalletId)
                .toReceiver(targetWalletId)
                .withAmount(amount, CurrencyType.QC)
                .viaAcquisition(AcquisitionType.PEER2PEER)
                .build();

        DebitCommand command = new DebitCommand(IdGenerator.generate(), transfer.getId(), sourceWalletId, amount);
        return transferRepository.insertTransferAndOutboxEvent(transfer, command).getId();
    }

        /**
     * Advances a transfer after a successful debit.
     * @param transferId input value
     */

    @Override
    public void handleDebitCompleted(UUID transferId) {
        Transfer transfer = getTransfer(transferId);
        TransferStatus prevStatus = transfer.getStatus();
        transfer.markSenderDebited();

        CreditCommand command = new CreditCommand(IdGenerator.generate(), transfer.getId(), transfer.getReceiverWalletId(), transfer.getAmount());
        transferRepository.updateTransferAndOutboxEvent(transfer, prevStatus, command);
    }

        /**
     * Records a failed debit for a transfer.
     * @param transferId input value
     * @param reason input value
     */

    @Override
    public void handleDebitFailed(UUID transferId, String reason) {
        Transfer transfer = getTransfer(transferId);
        TransferStatus prevStatus = transfer.getStatus();
        transfer.markFailure(reason);
        transferRepository.updateTransferAndOutboxEvent(transfer, prevStatus, null);
    }

        /**
     * Advances a transfer after a successful credit.
     * @param transferId input value
     */

    @Override
    public void handleCreditCompleted(UUID transferId) {
        Transfer transfer = getTransfer(transferId);
        TransferStatus prevStatus = transfer.getStatus();
        transfer.markSuccessful();
        transferRepository.updateTransferAndOutboxEvent(transfer, prevStatus, null);
    }

        /**
     * Starts refund processing after a failed credit.
     * @param transferId input value
     * @param reason input value
     */

    @Override
    public void handleCreditFailed(UUID transferId, String reason) {
        Transfer transfer = getTransfer(transferId);
        TransferStatus prevStatus = transfer.getStatus();
        transfer.markRefunding(reason);

        RefundCommand command = new RefundCommand(IdGenerator.generate(), transfer.getId(), transfer.getSenderWalletId(), transfer.getAmount());
        transferRepository.updateTransferAndOutboxEvent(transfer, prevStatus, command);
    }

        /**
     * Completes handling of a refunded transfer.
     * @param transferId input value
     */

    @Override
    public void handleRefundCompleted(UUID transferId) {
        Transfer transfer = getTransfer(transferId);
        TransferStatus prevStatus = transfer.getStatus();
        transfer.markFailure("Credit failed and debit was refunded");
        transferRepository.updateTransferAndOutboxEvent(transfer, prevStatus, null);
    }

    private Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferId));
    }
}
