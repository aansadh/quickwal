package com.quickwal.ledger.core.port.out;

import com.quickwal.ledger.core.domain.LedgerEntry;

import java.util.List;

/**
 * Defines the LedgerRepository interface used by QuickWal.
 */
public interface LedgerRepository {
    /**
     * Persists ledger entries.
     * @param ledgerEntries input value
     */
    void saveAll(List<LedgerEntry> ledgerEntries);
}
