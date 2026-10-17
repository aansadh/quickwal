package com.quickwal.wallet.core.service;

import com.quickwal.common.events.AggregateType;
import com.quickwal.common.events.MessageConsumer;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.command.CreditCommand;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.command.RefundCommand;
import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.CreditFailedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.DebitFailedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;
import com.quickwal.common.port.out.Inbox;
import com.quickwal.common.port.out.Outbox;
import com.quickwal.common.port.out.TransactionRunner;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.wallet.core.domain.exception.InsufficientFundsException;
import com.quickwal.wallet.core.domain.exception.WalletNotFoundException;
import com.quickwal.wallet.core.port.in.ProcessWalletCommand;
import com.quickwal.wallet.core.port.out.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;

public class WalletService implements ProcessWalletCommand {
    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository walletRepository;
    private final Inbox inbox;
    private final Outbox outbox;
    private final TransactionRunner transactionRunner;

    public WalletService(WalletRepository walletRepository, Inbox inbox, Outbox outbox,
                         TransactionRunner transactionRunner) {
        this.walletRepository = Objects.requireNonNull(walletRepository, "walletRepository");
        this.inbox = Objects.requireNonNull(inbox, "inbox");
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.transactionRunner = Objects.requireNonNull(transactionRunner, "transactionRunner");
    }

    @Override
    public void debit(DebitCommand command) {
        DebitCompletedEvent success = new DebitCompletedEvent(IdGenerator.generate(), command.transferId(),
                command.sourceWalletId(), command.amount().currency());
        process(command.eventId(), command.sourceWalletId(), success, () ->
                walletRepository.debit(command.sourceWalletId(), command.amount()),
                e -> new DebitFailedEvent(IdGenerator.generate(), command.transferId(),
                        command.sourceWalletId(), e.getMessage(), command.amount().currency()));
    }

    @Override
    public void credit(CreditCommand command) {
        CreditCompletedEvent success = new CreditCompletedEvent(IdGenerator.generate(), command.transferId(),
                command.targetWalletId(), command.amount().currency());
        process(command.eventId(), command.targetWalletId(), success, () ->
                walletRepository.credit(command.targetWalletId(), command.amount()),
                e -> new CreditFailedEvent(IdGenerator.generate(), command.transferId(),
                        command.targetWalletId(), e.getMessage(), command.amount().currency()));
    }

    @Override
    public void refund(RefundCommand command) {
        RefundCompletedEvent success = new RefundCompletedEvent(IdGenerator.generate(), command.transferId(),
                command.sourceWalletId(), command.amount().currency());
        transactionRunner.inTransaction(() -> {
            if (!inbox.markIfNew(command.eventId(), MessageConsumer.WALLET_WORKER)) {
                log.debug("Ignoring duplicate refund command: eventId={}, transferId={}",
                        command.eventId(), command.transferId());
                return;
            }
            walletRepository.refund(command.sourceWalletId(), command.amount());
            outbox.append(AggregateType.WALLET, command.sourceWalletId(), success);
            log.info("Refund processed: eventId={}, transferId={}, walletId={}",
                    command.eventId(), command.transferId(), command.sourceWalletId());
        });
    }

    private void process(UUID commandEventId, UUID walletId, Event successEvent, Runnable mutation,
                         java.util.function.Function<RuntimeException, Event> failureEvent) {
        try {
            transactionRunner.inTransaction(() -> {
                if (!inbox.markIfNew(commandEventId, MessageConsumer.WALLET_WORKER)) {
                    log.debug("Ignoring duplicate wallet command: eventId={}, walletId={}",
                            commandEventId, walletId);
                    return;
                }
                mutation.run();
                outbox.append(AggregateType.WALLET, walletId, successEvent);
                log.info("Wallet command processed: eventId={}, walletId={}", commandEventId, walletId);
            });
        } catch (IllegalArgumentException | InsufficientFundsException | WalletNotFoundException e) {
            log.warn("Wallet command rejected: eventId={}, walletId={}, reason={}",
                    commandEventId, walletId, e.getMessage());
            Event failed = failureEvent.apply(e);
            transactionRunner.inTransaction(() -> {
                if (inbox.markIfNew(commandEventId, MessageConsumer.WALLET_WORKER)) {
                    outbox.append(AggregateType.WALLET, walletId, failed);
                }
            });
        }
    }
}
