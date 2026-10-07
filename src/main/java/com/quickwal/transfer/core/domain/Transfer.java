package com.quickwal.transfer.core.domain;

import com.quickwal.common.utils.IdGenerator;
import lombok.Getter;

import java.util.UUID;

@Getter
public class Transfer {
    private final UUID id;
    private final UUID idempotencyKey;
    private final UUID senderWalletId;
    private final UUID receiverWalletId;
    private final long amount;
    private final CurrencyType currency;
    private final AcquisitionType acquisition;

    private TransferStatus status;
    private String msg;

    private Transfer(Builder builder) {
        this.id = builder.id;
        this.idempotencyKey = builder.idempotencyKey;
        this.senderWalletId = builder.senderWalletId;
        this.receiverWalletId = builder.receiverWalletId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.acquisition = builder.acquisition;

        this.status = TransferStatus.PROCESSING;
        this.msg = "Transfer Initiated";
    }

    public static Builder initiate(UUID idempotencyKey) {
        return new Builder(idempotencyKey);
    }

    public static class Builder {
        private final UUID idempotencyKey; // Required
        private final UUID id;
        private UUID senderWalletId;
        private UUID receiverWalletId;
        private long amount;
        private CurrencyType currency;
        private AcquisitionType acquisition;

        public Builder(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            this.id = IdGenerator.generate();
        }

        public Builder fromSender(UUID senderWalletId) {
            this.senderWalletId = senderWalletId;
            return this;
        }

        public Builder toReceiver(UUID receiverWalletId) {
            this.receiverWalletId = receiverWalletId;
            return this;
        }

        public Builder withAmount(long amount, CurrencyType currency) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            this.amount = amount;
            this.currency = currency;
            return this;
        }

        public Builder viaAcquisition(AcquisitionType acquisition) {
            this.acquisition = acquisition;
            return this;
        }

        public Transfer build() {
            if (senderWalletId == null || receiverWalletId == null) {
                throw new IllegalStateException("Sender and Receiver must be specified");
            }
            return new Transfer(this);
        }
    }

    public void markSuccessful() {
        if (this.status != TransferStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING transactions can be marked SUCCESSFUL");
        }
        this.status = TransferStatus.SUCCESSFUL;
    }

    public void markFailure(String failureMessage) {
        if (this.status != TransferStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING transactions can be marked FAILURE");
        }
        this.status = TransferStatus.FAILURE;
        this.msg = failureMessage;
    }

    public void abort(String abortMessage) {
        this.status = TransferStatus.ABORTED;
        this.msg = abortMessage;
    }
}