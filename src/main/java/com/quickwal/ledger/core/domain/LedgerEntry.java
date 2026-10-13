package com.quickwal.ledger.core.domain;

import java.util.UUID;

/**
 * Represents the LedgerEntry record used by QuickWal.
 */
public record LedgerEntry(
        UUID id,
        UUID transferId,
        UUID walletId,
        EntryType entryType,
        long amount
) {
    public LedgerEntry {
        if (amount <= 0) {

            throw new IllegalArgumentException("Amount must be positive");
        }
    }
}
