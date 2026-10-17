package com.quickwal.transfer.core.port.out;

import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.domain.TransferStatus;
import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
    Transfer insert(Transfer transfer);
    void update(Transfer transfer, TransferStatus expectedPreviousStatus);
    Optional<Transfer> findById(UUID transferId);
}
