package com.quickwal.transfer.core.port.in;

import com.quickwal.transfer.core.domain.Transfer;
import java.util.UUID;

/** Inbound query use case for retrieving a transfer and its current saga state. */
public interface GetTransfer {
    /**
     * Retrieves a transfer by its identifier.
     *
     * @param transferId identifier of the transfer to retrieve
     * @return the current transfer state
     * @throws com.quickwal.transfer.core.domain.exception.TransferNotFoundException if the transfer does not exist
     */
    Transfer get(UUID transferId);
}
