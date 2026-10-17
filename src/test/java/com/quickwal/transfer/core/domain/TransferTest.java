package com.quickwal.transfer.core.domain;

import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferTest {

    private Transfer newTransfer() {
        return Transfer.initiate(UUID.randomUUID())
                .fromSender(UUID.randomUUID())
                .toReceiver(UUID.randomUUID())
                .withAmount(new Money(500L, CurrencyType.QC))
                .viaAcquisition(AcquisitionType.PEER2PEER)
                .build();
    }

    @Test
    void initiate_createsTransferInProcessingState() {
        UUID idempotencyKey = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        UUID receiverId = UUID.randomUUID();

        Transfer transfer = Transfer.initiate(idempotencyKey)
                .fromSender(senderId)
                .toReceiver(receiverId)
                .withAmount(new Money(500L, CurrencyType.QC))
                .viaAcquisition(AcquisitionType.PEER2PEER)
                .build();

        assertNotNull(transfer.getId());
        assertEquals(idempotencyKey, transfer.getIdempotencyKey());
        assertEquals(senderId, transfer.getSenderWalletId());
        assertEquals(receiverId, transfer.getReceiverWalletId());
        assertEquals(new Money(500L, CurrencyType.QC), transfer.getAmount());
        assertEquals(AcquisitionType.PEER2PEER, transfer.getAcquisition());
        assertEquals(TransferStatus.PROCESSING, transfer.getStatus());
    }

    @Test
    void withAmount_rejectsZero() {
        assertThrows(IllegalArgumentException.class, () ->
                Transfer.initiate(UUID.randomUUID())
                        .fromSender(UUID.randomUUID())
                        .toReceiver(UUID.randomUUID())
                        .withAmount(new Money(0L, CurrencyType.QC))
        );
    }

    @Test
    void withAmount_rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () ->
                Transfer.initiate(UUID.randomUUID())
                        .fromSender(UUID.randomUUID())
                        .toReceiver(UUID.randomUUID())
                        .withAmount(new Money(-1L, CurrencyType.QC))
        );
    }

    @Test
    void build_rejectsMissingSender() {
        assertThrows(IllegalStateException.class, () ->
                Transfer.initiate(UUID.randomUUID())
                        .toReceiver(UUID.randomUUID())
                        .withAmount(new Money(500L, CurrencyType.QC))
                        .viaAcquisition(AcquisitionType.PEER2PEER)
                        .build()
        );
    }

    @Test
    void build_rejectsMissingReceiver() {
        assertThrows(IllegalStateException.class, () ->
                Transfer.initiate(UUID.randomUUID())
                        .fromSender(UUID.randomUUID())
                        .withAmount(new Money(500L, CurrencyType.QC))
                        .viaAcquisition(AcquisitionType.PEER2PEER)
                        .build()
        );
    }

    @Test
    void markSenderDebited_movesProcessingTransferToSenderDebited() {
        Transfer transfer = newTransfer();

        transfer.markSenderDebited();

        assertEquals(TransferStatus.SENDER_DEBITED, transfer.getStatus());
    }

    @Test
    void markSenderDebited_rejectsInvalidState() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();
        transfer.markSuccessful();

        assertThrows(
                IllegalStateException.class,
                transfer::markSenderDebited
        );
    }

    @Test
    void markSuccessful_requiresSenderToBeDebited() {
        Transfer transfer = newTransfer();

        assertThrows(
                IllegalStateException.class,
                transfer::markSuccessful
        );
    }

    @Test
    void markSuccessful_completesTransferAfterDebit() {
        Transfer transfer = newTransfer();

        transfer.markSenderDebited();
        transfer.markSuccessful();

        assertEquals(TransferStatus.SUCCESSFUL, transfer.getStatus());
    }

    @Test
    void markRefunding_requiresSenderToBeDebited() {
        Transfer transfer = newTransfer();

        assertThrows(
                IllegalStateException.class,
                () -> transfer.markRefunding("Credit failed")
        );
    }

    @Test
    void markRefunding_movesSenderDebitedTransferToRefunding() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();

        transfer.markRefunding("Credit failed");

        assertEquals(TransferStatus.REFUNDING, transfer.getStatus());
    }

    @Test
    void markDebitFailed_rejectsSuccessfulTransfer() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();
        transfer.markSuccessful();

        assertThrows(
                IllegalStateException.class,
                () -> transfer.markDebitFailed("Unexpected failure")
        );
    }

    @Test
    void abort_rejectsSuccessfulTransfer() {
        Transfer transfer = newTransfer();
        transfer.markSenderDebited();
        transfer.markSuccessful();

        assertThrows(
                IllegalStateException.class,
                () -> transfer.abort("Abort requested")
        );
    }
}

