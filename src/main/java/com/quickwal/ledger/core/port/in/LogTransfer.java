package com.quickwal.ledger.core.port.in;

import java.util.UUID;

public interface LogTransfer {
    void log(UUID transferId, UUID senderWalletId, UUID receiverWalletId, long amount);
}
