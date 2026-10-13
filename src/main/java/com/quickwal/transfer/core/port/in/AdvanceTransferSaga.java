package com.quickwal.transfer.core.port.in;

import java.util.UUID;

/**
 * Inbound use-case port for applying asynchronous wallet outcomes to a
 * transfer.
 *
 * <p>Implementations persist each state transition and may emit the next
 * command through the transactional outbox. Failure callbacks represent saga
 * compensation rather than message-delivery guarantees.</p>
 */
public interface AdvanceTransferSaga {
    /**
     * Advances a processing transfer after its source wallet was debited.
     *
     * @param transferId transfer to advance
     */
    void handleDebitCompleted(UUID transferId);
    /**
     * Completes a transfer after its destination wallet was credited.
     *
     * @param transferId transfer to complete
     */
    void handleCreditCompleted(UUID transferId);

    /**
     * Marks a transfer as failed when its source debit cannot be completed.
     *
     * @param transferId transfer affected by the failure
     * @param reason failure description
     */
    void handleDebitFailed(UUID transferId, String reason);
    /**
     * Starts compensation when destination credit fails after a successful
     * source debit.
     *
     * @param transferId transfer requiring compensation
     * @param reason failure description
     */
    void handleCreditFailed(UUID transferId, String reason);
    /**
     * Marks a compensated transfer as failed after the source refund completes.
     *
     * @param transferId transfer whose debit was refunded
     */
    void handleRefundCompleted(UUID transferId);
}
