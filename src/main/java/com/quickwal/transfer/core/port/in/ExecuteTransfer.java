package com.quickwal.transfer.core.port.in;

import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.transfer.core.domain.CurrencyType;
import com.quickwal.transfer.core.domain.Transfer;

import java.util.UUID;

public interface ExecuteTransfer {
    Transfer execute(
            UUID idempotencyKey,
            UUID senderId,
            UUID receiverId,
            long amount,
            CurrencyType currency,
            AcquisitionType acquisition
    );
}
