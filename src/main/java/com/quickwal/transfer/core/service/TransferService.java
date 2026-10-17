package com.quickwal.transfer.core.service;

import com.quickwal.common.events.AggregateType;
import com.quickwal.common.events.MessageConsumer;
import com.quickwal.common.messages.command.CreditCommand;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.command.RefundCommand;
import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.CreditFailedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.DebitFailedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;
import com.quickwal.common.money.Money;
import com.quickwal.common.port.out.Inbox;
import com.quickwal.common.port.out.Outbox;
import com.quickwal.common.port.out.TransactionRunner;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.domain.TransferStatus;
import com.quickwal.transfer.core.domain.exception.TransferNotFoundException;
import com.quickwal.transfer.core.port.in.AdvanceTransferSaga;
import com.quickwal.transfer.core.port.in.InitiateTransfer;
import com.quickwal.transfer.core.port.in.GetTransfer;
import com.quickwal.transfer.core.port.out.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class TransferService implements InitiateTransfer, GetTransfer, AdvanceTransferSaga {
    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    private final TransferRepository transferRepository;
    private final Inbox inbox;
    private final Outbox outbox;
    private final TransactionRunner transactionRunner;

    public TransferService(TransferRepository transferRepository, Inbox inbox,
                           Outbox outbox, TransactionRunner transactionRunner) {
        this.transferRepository = Objects.requireNonNull(transferRepository, "transferRepository");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
    }

    @Override
    public UUID execute(UUID sourceWalletId, UUID targetWalletId, Money amount, String idempotencyKey) {
        UUID idempotencyId = UUID.fromString(idempotencyKey);
        Transfer requested = Transfer.initiate(idempotencyId)
                .fromSender(sourceWalletId)
                .toReceiver(targetWalletId)
                .withAmount(amount)
                .viaAcquisition(AcquisitionType.PEER2PEER)
                .build();
        DebitCommand command = new DebitCommand(IdGenerator.generate(), requested.getId(), sourceWalletId, amount);

        UUID transferId = transactionRunner.inTransaction(() -> {
            Transfer persisted = transferRepository.insert(requested);
            if (persisted.getId().equals(requested.getId())) {
                outbox.append(AggregateType.TRANSFER, persisted.getId(), command);
            }
            return persisted.getId();
        });
        log.info("Transfer accepted: transferId={}, sourceWalletId={}, targetWalletId={}, currency={}",
                transferId, sourceWalletId, targetWalletId, amount.currency().code());
        return transferId;
    }

    @Override
    public void handleDebitCompleted(DebitCompletedEvent event) {
        transactionRunner.inTransaction(() -> {
            if (isDuplicate(event.eventId(), event.transferId())) return;
            Transfer transfer = getTransfer(event.transferId());
            transfer.validateSourceEvent(event.sourceWalletId(), event.currency());
            TransferStatus previous = transfer.getStatus();
            transfer.markSenderDebited();
            CreditCommand command = new CreditCommand(IdGenerator.generate(), transfer.getId(),
                    transfer.getReceiverWalletId(), transfer.getAmount());
            transferRepository.update(transfer, previous);
            outbox.append(AggregateType.TRANSFER, transfer.getId(), command);
            logTransition(event.eventId(), transfer, previous);
        });
    }

    @Override
    public void handleDebitFailed(DebitFailedEvent event) {
        transactionRunner.inTransaction(() -> {
            if (isDuplicate(event.eventId(), event.transferId())) return;
            Transfer transfer = getTransfer(event.transferId());
            transfer.validateSourceEvent(event.sourceWalletId(), event.currency());
            TransferStatus previous = transfer.getStatus();
            transfer.markDebitFailed(event.reason());
            transferRepository.update(transfer, previous);
            logTransition(event.eventId(), transfer, previous);
        });
    }

    @Override
    public void handleCreditCompleted(CreditCompletedEvent event) {
        transactionRunner.inTransaction(() -> {
            if (isDuplicate(event.eventId(), event.transferId())) return;
            Transfer transfer = getTransfer(event.transferId());
            transfer.validateTargetEvent(event.targetWalletId(), event.currency());
            TransferStatus previous = transfer.getStatus();
            transfer.markSuccessful();
            transferRepository.update(transfer, previous);
            logTransition(event.eventId(), transfer, previous);
        });
    }

    @Override
    public void handleCreditFailed(CreditFailedEvent event) {
        transactionRunner.inTransaction(() -> {
            if (isDuplicate(event.eventId(), event.transferId())) return;
            Transfer transfer = getTransfer(event.transferId());
            transfer.validateTargetEvent(event.targetWalletId(), event.currency());
            TransferStatus previous = transfer.getStatus();
            transfer.markRefunding(event.reason());
            RefundCommand command = new RefundCommand(IdGenerator.generate(), transfer.getId(),
                    transfer.getSenderWalletId(), transfer.getAmount());
            transferRepository.update(transfer, previous);
            outbox.append(AggregateType.TRANSFER, transfer.getId(), command);
            logTransition(event.eventId(), transfer, previous);
        });
    }

    @Override
    public void handleRefundCompleted(RefundCompletedEvent event) {
        transactionRunner.inTransaction(() -> {
            if (isDuplicate(event.eventId(), event.transferId())) return;
            Transfer transfer = getTransfer(event.transferId());
            transfer.validateSourceEvent(event.sourceWalletId(), event.currency());
            TransferStatus previous = transfer.getStatus();
            transfer.markRefundCompleted();
            transferRepository.update(transfer, previous);
            logTransition(event.eventId(), transfer, previous);
        });
    }

    /**
     * Returns the current state of a transfer for inbound query adapters.
     *
     * @param transferId identifier of the transfer to retrieve
     * @return the persisted transfer state
     * @throws TransferNotFoundException if no transfer has that identifier
     */
    @Override
    public Transfer get(UUID transferId) {
        return getTransfer(transferId);
    }

    private Transfer getTransfer(UUID transferId) {
        return transferRepository.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException("Transfer not found: " + transferId));
    }

    /**
     * Records inbox idempotency and logs when an event was already applied.
     *
     * @param eventId identifier of the consumed event
     * @param transferId identifier of the associated transfer
     * @return whether the event had already been processed
     */
    private boolean isDuplicate(UUID eventId, UUID transferId) {
        if (inbox.markIfNew(eventId, MessageConsumer.TRANSFER_WORKER)) {
            return false;
        }
        log.debug("Ignoring duplicate transfer event: eventId={}, transferId={}", eventId, transferId);
        return true;
    }

    /**
     * Logs a successfully persisted transfer saga status transition.
     *
     * @param eventId event that advanced the saga
     * @param transfer transfer after the state transition
     * @param previousStatus status before the transition
     */
    private void logTransition(UUID eventId, Transfer transfer, TransferStatus previousStatus) {
        log.info("Transfer saga state changed: transferId={}, eventId={}, fromStatus={}, toStatus={}",
                transfer.getId(), eventId, previousStatus.code(), transfer.getStatus().code());
    }
}

