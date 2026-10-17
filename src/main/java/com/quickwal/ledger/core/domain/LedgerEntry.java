package com.quickwal.ledger.core.domain;

import java.util.UUID;
import com.quickwal.common.money.Money;

/**
 * Represents the LedgerEntry record used by QuickWal.
 */
public record LedgerEntry(
        UUID id,
        UUID transferId,
        UUID walletId,
        EntryType entryType,
        Money amount
) {
    public LedgerEntry {
        amount.requirePositive();
    }
}
