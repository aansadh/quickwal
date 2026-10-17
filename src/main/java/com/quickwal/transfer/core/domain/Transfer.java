package com.quickwal.transfer.core.domain;

import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;
import com.quickwal.common.utils.IdGenerator;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

@Getter
public class Transfer {
    private final UUID id;
    private final UUID idempotencyKey;
    private final UUID senderWalletId;
    private final UUID receiverWalletId;
    private final Money amount;
    private final AcquisitionType acquisition;
    private TransferStatus status;
    private String msg;

    private Transfer(Builder builder) {
        id = builder.id;
        idempotencyKey = builder.idempotencyKey;
        senderWalletId = builder.senderWalletId;
        receiverWalletId = builder.receiverWalletId;
        amount = builder.amount;
        acquisition = builder.acquisition;
        status = TransferStatus.PROCESSING;
        msg = "Transfer Initiated";
    }

    private Transfer(UUID id, UUID idempotencyKey, UUID senderWalletId, UUID receiverWalletId,
                     Money amount, AcquisitionType acquisition, TransferStatus status, String msg) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.senderWalletId = senderWalletId;
        this.receiverWalletId = receiverWalletId;
        this.amount = amount;
        this.acquisition = acquisition;
        this.status = status;
        this.msg = msg;
    }

    public static Transfer hydrate(UUID id, UUID idempotencyKey, UUID senderWalletId, UUID receiverWalletId,
                                   Money amount, AcquisitionType acquisition, TransferStatus status, String msg) {
        return new Transfer(id, idempotencyKey, senderWalletId, receiverWalletId, amount, acquisition, status, msg);
    }

    public static Builder initiate(UUID idempotencyKey) { return new Builder(idempotencyKey); }

    public static class Builder {
        private final UUID idempotencyKey;
        private final UUID id = IdGenerator.generate();
        private UUID senderWalletId;
        private UUID receiverWalletId;
        private Money amount;
        private AcquisitionType acquisition;

        public Builder(UUID idempotencyKey) {
            this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        }
        public Builder fromSender(UUID senderWalletId) { this.senderWalletId = senderWalletId; return this; }
        public Builder toReceiver(UUID receiverWalletId) { this.receiverWalletId = receiverWalletId; return this; }
        public Builder withAmount(Money amount) {
            this.amount = Objects.requireNonNull(amount, "amount");
            amount.requirePositive();
            return this;
        }
        public Builder viaAcquisition(AcquisitionType acquisition) {
            this.acquisition = Objects.requireNonNull(acquisition, "acquisition");
            return this;
        }
        public Transfer build() {
            if (senderWalletId == null || receiverWalletId == null) throw new IllegalStateException("Sender and receiver wallets must be specified");
            if (amount == null) throw new IllegalStateException("Amount must be specified");
            if (senderWalletId.equals(receiverWalletId)) {
                throw new IllegalArgumentException("Sender and receiver wallets must differ");
            }
            if (acquisition == null) throw new IllegalStateException("Acquisition type must be specified");
            return new Transfer(this);
        }
    }

    public void validateSourceEvent(UUID walletId, CurrencyType currency) {
        validateEvent(walletId, currency, senderWalletId);
    }

    public void validateTargetEvent(UUID walletId, CurrencyType currency) {
        validateEvent(walletId, currency, receiverWalletId);
    }

    private void validateEvent(UUID walletId, CurrencyType currency, UUID expectedWalletId) {
        Objects.requireNonNull(walletId, "event walletId");
        Objects.requireNonNull(currency, "event currency");
        if (!expectedWalletId.equals(walletId)) throw new IllegalArgumentException("Event wallet does not match transfer wallet");
        if (amount.currency() != currency) throw new IllegalArgumentException("Event currency does not match transfer currency");
    }

    public void markSenderDebited() {
        requireStatus(TransferStatus.PROCESSING, "debit completion");
        status = TransferStatus.SENDER_DEBITED;
        msg = "Sender debited, awaiting receiver credit";
    }

    public void markDebitFailed(String reason) {
        requireStatus(TransferStatus.PROCESSING, "debit failure");
        markFailure(reason);
    }

    public void markRefunding(String reason) {
        requireStatus(TransferStatus.SENDER_DEBITED, "refund");
        requireReason(reason);
        status = TransferStatus.REFUNDING;
        msg = "Refunding: " + reason;
    }

    public void markSuccessful() {
        requireStatus(TransferStatus.SENDER_DEBITED, "completion");
        status = TransferStatus.SUCCESSFUL;
        msg = "Transfer Completed Successfully";
    }

    public void markRefundCompleted() {
        requireStatus(TransferStatus.REFUNDING, "refund completion");
        markFailure("Credit failed and debit was refunded");
    }

    private void markFailure(String reason) {
        requireReason(reason);
        status = TransferStatus.FAILURE;
        msg = reason;
    }

    private void requireStatus(TransferStatus expected, String operation) {
        if (status != expected) throw new IllegalStateException("Invalid transition: " + operation + " requires " + expected);
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Failure reason is required");
    }

    public void abort(String abortMessage) {
        if (status == TransferStatus.SUCCESSFUL) throw new IllegalStateException("Invalid transition: Cannot abort a SUCCESSFUL transfer");
        status = TransferStatus.ABORTED;
        msg = abortMessage;
    }
}




