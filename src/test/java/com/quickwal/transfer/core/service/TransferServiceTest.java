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
import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;
import com.quickwal.common.port.out.Inbox;
import com.quickwal.common.port.out.Outbox;
import com.quickwal.common.port.out.TransactionRunner;
import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.domain.TransferStatus;
import com.quickwal.transfer.core.domain.exception.TransferNotFoundException;
import com.quickwal.transfer.core.port.out.TransferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {
    @Mock private TransferRepository transferRepository;
    @Mock private Inbox inbox;
    @Mock private Outbox outbox;
    private TransferService transferService;

    @BeforeEach
    void setUp() {
        TransactionRunner transactionRunner = new TransactionRunner() {
            @Override public <T> T inTransaction(Supplier<T> work) { return work.get(); }
        };
        transferService = new TransferService(transferRepository, inbox, outbox, transactionRunner);
        lenient().when(inbox.markIfNew(any(), eq(MessageConsumer.TRANSFER_WORKER))).thenReturn(true);
    }

    private Transfer newTransfer() {
        return Transfer.initiate(UUID.randomUUID()).fromSender(UUID.randomUUID()).toReceiver(UUID.randomUUID())
                .withAmount(new Money(500L, CurrencyType.QC)).viaAcquisition(AcquisitionType.PEER2PEER).build();
    }

    @Test
    void executeCreatesTransferAndOutboxDebitCommand() {
        UUID source = UUID.randomUUID(), target = UUID.randomUUID(), key = UUID.randomUUID();
        when(transferRepository.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID result = transferService.execute(source, target, new Money(500, CurrencyType.QC), key.toString());

        verify(transferRepository).insert(argThat(t -> t.getId().equals(result)
                && t.getIdempotencyKey().equals(key) && t.getSenderWalletId().equals(source)
                && t.getReceiverWalletId().equals(target)));
        verify(outbox).append(eq(AggregateType.TRANSFER), eq(result), isA(DebitCommand.class));
    }

    @Test
    void executeRejectsMalformedIdempotencyKey() {
        assertThrows(IllegalArgumentException.class, () -> transferService.execute(UUID.randomUUID(), UUID.randomUUID(),
                new Money(500, CurrencyType.QC), "not-a-uuid"));
        verifyNoInteractions(transferRepository, outbox);
    }

    @Test
    void debitCompletedValidatesEventAndEmitsCreditCommand() {
        Transfer transfer = newTransfer();
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        DebitCompletedEvent event = new DebitCompletedEvent(UUID.randomUUID(), transfer.getId(),
                transfer.getSenderWalletId(), CurrencyType.QC);

        transferService.handleDebitCompleted(event);

        assertEquals(TransferStatus.SENDER_DEBITED, transfer.getStatus());
        verify(transferRepository).update(transfer, TransferStatus.PROCESSING);
        verify(outbox).append(eq(AggregateType.TRANSFER), eq(transfer.getId()), isA(CreditCommand.class));
    }

    @Test
    void failedDebitMovesTransferToFailure() {
        Transfer transfer = newTransfer();
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        transferService.handleDebitFailed(new DebitFailedEvent(UUID.randomUUID(), transfer.getId(),
                transfer.getSenderWalletId(), "Insufficient funds", CurrencyType.QC));
        assertEquals(TransferStatus.FAILURE, transfer.getStatus());
        verify(transferRepository).update(transfer, TransferStatus.PROCESSING);
    }

    @Test
    void creditFailureStartsRefund() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        transferService.handleCreditFailed(new CreditFailedEvent(UUID.randomUUID(), transfer.getId(),
                transfer.getReceiverWalletId(), "Credit failed", CurrencyType.QC));
        assertEquals(TransferStatus.REFUNDING, transfer.getStatus());
        verify(transferRepository).update(transfer, TransferStatus.SENDER_DEBITED);
        verify(outbox).append(eq(AggregateType.TRANSFER), eq(transfer.getId()), isA(RefundCommand.class));
    }

    @Test
    void creditCompletionMarksTransferSuccessfulWithoutLedgerDependency() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();
        when(transferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        transferService.handleCreditCompleted(new CreditCompletedEvent(UUID.randomUUID(), transfer.getId(),
                transfer.getReceiverWalletId(), CurrencyType.QC));
        assertEquals(TransferStatus.SUCCESSFUL, transfer.getStatus());
        verify(transferRepository).update(transfer, TransferStatus.SENDER_DEBITED);
    }

    @Test
    void duplicateEventDoesNotAdvanceSaga() {
        when(inbox.markIfNew(any(), eq(MessageConsumer.TRANSFER_WORKER))).thenReturn(false);
        transferService.handleRefundCompleted(new RefundCompletedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), CurrencyType.QC));
        verifyNoInteractions(transferRepository, outbox);
    }

    @Test
    void sagaHandlerFailsForUnknownTransfer() {
        UUID id = UUID.randomUUID();
        when(transferRepository.findById(id)).thenReturn(Optional.empty());
        assertThrows(TransferNotFoundException.class, () -> transferService.handleDebitCompleted(
                new DebitCompletedEvent(UUID.randomUUID(), id, UUID.randomUUID(), CurrencyType.QC)));
        verify(transferRepository, never()).update(any(), any());
    }
}

