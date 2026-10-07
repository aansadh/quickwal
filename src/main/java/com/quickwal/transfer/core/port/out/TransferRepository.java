package com.quickwal.transfer.core.port.out;

import com.quickwal.transfer.core.domain.Transfer;

public interface TransferRepository {
    void save(Transfer transfer);
    Transfer saveIdempotently(Transfer transfer);
    void update(Transfer transfer);
}
