package com.quickwal.transfer.core.domain;

import com.quickwal.common.utils.IdGenerator;
import lombok.Getter;
import java.util.UUID;

/**
 * Provides the Transfer class used by QuickWal.
 */

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

    private Transfer(UUID id, UUID idempotencyKey, UUID senderWalletId, UUID receiverWalletId, long amount, CurrencyType currency, AcquisitionType acquisition, TransferStatus status, String msg) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.senderWalletId = senderWalletId;
        this.receiverWalletId = receiverWalletId;
        this.amount = amount;
        this.currency = currency;
        this.acquisition = acquisition;
        this.status = status;
        this.msg = msg;
    }

    /**
     * Reconstructs a transfer from persisted state.
     * @param id input value
     * @param idempotencyKey input value
     * @param senderWalletId input value
     * @param receiverWalletId input value
     * @param amount input value
     * @param currency input value
     * @param acquisition input value
     * @param status input value
     * @param msg input value
     * @return the operation result
     */
    public static Transfer hydrate(UUID id, UUID idempotencyKey, UUID senderWalletId, UUID receiverWalletId, long amount, CurrencyType currency, AcquisitionType acquisition, TransferStatus status, String msg) {

        return new Transfer(id, idempotencyKey, senderWalletId, receiverWalletId, amount, currency, acquisition, status, msg);
    }

    /**
     * Starts building a new transfer.
     * @param idempotencyKey input value
     * @return the operation result
     */
    public static Builder initiate(UUID idempotencyKey) {

        return new Builder(idempotencyKey);
    }

    public static class Builder {
        private final UUID idempotencyKey;
        private final UUID id;
        private UUID senderWalletId;
        private UUID receiverWalletId;
        private long amount;
        private CurrencyType currency;
        private AcquisitionType acquisition;

        /**
         * Performs the Builder operation.
         * @param idempotencyKey input value
         */
        public Builder(UUID idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            this.id = IdGenerator.generate();
        }

        /**
         * Sets the sender wallet.
         * @param senderWalletId input value
         * @return the operation result
         */
        public Builder fromSender(UUID senderWalletId) {
            this.senderWalletId = senderWalletId;
            return this;
        }

        /**
         * Sets the receiver wallet.
         * @param receiverWalletId input value
         * @return the operation result
         */
        public Builder toReceiver(UUID receiverWalletId) {
            this.receiverWalletId = receiverWalletId;
            return this;
        }

        /**
         * Sets the transfer amount and currency.
         * @param amount input value
         * @param currency input value
         * @return the operation result
         */
        public Builder withAmount(long amount, CurrencyType currency) {
            if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
            this.amount = amount;
            this.currency = currency;
            return this;
        }

        /**
         * Sets the transfer acquisition type.
         * @param acquisition input value
         * @return the operation result
         */
        public Builder viaAcquisition(AcquisitionType acquisition) {
            this.acquisition = acquisition;
            return this;
        }

        /**
         * Builds the transfer.
         * @return the operation result
         */
        public Transfer build() {
            if (senderWalletId == null || receiverWalletId == null) {

                throw new IllegalStateException("Sender and Receiver must be specified");
            }

            return new Transfer(this);
        }
    }

    /**
     * Marks the sender debit as completed.
     */
    public void markSenderDebited() {
        if (this.status != TransferStatus.PROCESSING) {

            throw new IllegalStateException("Invalid transition: Transfer is not PROCESSING");
        }
        this.status = TransferStatus.SENDER_DEBITED;
        this.msg = "Sender debited, awaiting receiver credit";
    }

    /**
     * Marks the transfer as being refunded.
     * @param reason input value
     */
    public void markRefunding(String reason) {
        if (this.status != TransferStatus.SENDER_DEBITED) {

            throw new IllegalStateException("Invalid transition: Cannot refund unless SENDER_DEBITED");
        }
        this.status = TransferStatus.REFUNDING;
        this.msg = "Refunding: " + reason;
    }

    /**
     * Marks the transfer as successful.
     */
    public void markSuccessful() {
        if (this.status != TransferStatus.SENDER_DEBITED) {

            throw new IllegalStateException("Invalid transition: Cannot complete unless SENDER_DEBITED");
        }
        this.status = TransferStatus.SUCCESSFUL;
        this.msg = "Transfer Completed Successfully";
    }

    /**
     * Marks the transfer as failed.
     * @param failureMessage input value
     */
    public void markFailure(String failureMessage) {
        if (this.status == TransferStatus.SUCCESSFUL) {

            throw new IllegalStateException("Invalid transition: Cannot fail a SUCCESSFUL transfer");
        }
        this.status = TransferStatus.FAILURE;
        this.msg = failureMessage;
    }

    /**
     * Aborts the transfer.
     * @param abortMessage input value
     */
    public void abort(String abortMessage) {
        if (this.status == TransferStatus.SUCCESSFUL) {

            throw new IllegalStateException("Invalid transition: Cannot abort a SUCCESSFUL transfer");
        }
        this.status = TransferStatus.ABORTED;
        this.msg = abortMessage;
    }
}
