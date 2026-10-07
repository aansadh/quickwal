package com.quickwal.ledger.core.port.out;

import com.quickwal.ledger.core.domain.LedgerEntry;

import java.util.List;

public interface LedgerRepository {
    void saveAll(List<LedgerEntry> ledgerEntries);
}
