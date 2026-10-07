package com.quickwal.transfer.core.domain;

import com.quickwal.common.utils.IdGenerator;
import lombok.Getter;

import java.util.UUID;

@Getter
public class Transaction {
    private final UUID id;
    private final String idempotencyKey;
    private final UUID senderWalletId;
    private final UUID receiverWalletId;
    private final long amount;
    private final CurrencyType currency;
    private final AcquisitionType acquisition;

    private TransactionStatus status;
    private String msg;

    private Transaction(Builder builder) {
        this.id = builder.id;
        this.idempotencyKey = builder.idempotencyKey;
        this.senderWalletId = builder.senderWalletId;
        this.receiverWalletId = builder.receiverWalletId;
        this.amount = builder.amount;
        this.currency = builder.currency;
        this.acquisition = builder.acquisition;

        this.status = TransactionStatus.PROCESSING;
        this.msg = "Transfer Initiated";
    }

    public static Builder initiate(String idempotencyKey) {
        return new Builder(idempotencyKey);
    }

    public static class Builder {
        private final String idempotencyKey; // Required
        private final UUID id;
        private UUID senderWalletId;
        private UUID receiverWalletId;
        private long amount;
        private CurrencyType currency;
        private AcquisitionType acquisition;

        public Builder(String idempotencyKey) {
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

        public Transaction build() {
            if (senderWalletId == null || receiverWalletId == null) {
                throw new IllegalStateException("Sender and Receiver must be specified");
            }
            return new Transaction(this);
        }
    }

    public void markSuccessful() {
        if (this.status != TransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING transactions can be marked SUCCESSFUL");
        }
        this.status = TransactionStatus.SUCCESSFUL;
    }

    public void markFailure(String failureMessage) {
        if (this.status != TransactionStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING transactions can be marked FAILURE");
        }
        this.status = TransactionStatus.FAILURE;
        this.msg = failureMessage;
    }

    public void abort(String abortMessage) {
        this.status = TransactionStatus.ABORTED;
        this.msg = abortMessage;
    }
}