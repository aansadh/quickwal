package com.quickwal.ledger.core.port.in;

import java.util.UUID;
import com.quickwal.common.money.Money;

/**
 * Defines the LogTransfer interface used by QuickWal.
 */
public interface LogTransfer {
    /**
     * Logs the transfer in the ledger.
     * @param transferId input value
     * @param senderWalletId input value
     * @param receiverWalletId input value
     * @param amount input value
     */
    void log(UUID transferId, UUID senderWalletId, UUID receiverWalletId, Money amount);
}
