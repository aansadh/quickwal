package com.quickwal.transfer.core.port.out;

import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.domain.TransferStatus;
import com.quickwal.common.messages.Command;

import java.util.Optional;
import java.util.UUID;

/**
 * Defines the TransferRepository interface used by QuickWal.
 */
public interface TransferRepository {
    /**
     * Performs the insertTransferAndOutboxEvent operation.
     * @param transfer input value
     * @param commandPayload input value
     * @return the operation result
     */
    Transfer insertTransferAndOutboxEvent(Transfer transfer, Command commandPayload);
    /**
     * Performs the updateTransferAndOutboxEvent operation.
     * @param transfer input value
     * @param expectedPreviousStatus input value
     * @param commandPayload input value
     */
    void updateTransferAndOutboxEvent(Transfer transfer, TransferStatus expectedPreviousStatus, Command commandPayload);
    /**
     * Finds a transfer by its identifier.
     * @param transferId input value
     * @return the operation result
     */
    Optional<Transfer> findById(UUID transferId);
}
