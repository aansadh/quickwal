package com.quickwal.ledger.core.service;

import com.quickwal.common.utils.IdGenerator;
import com.quickwal.ledger.core.domain.EntryType;
import com.quickwal.ledger.core.domain.LedgerEntry;
import com.quickwal.ledger.core.port.in.LogTransfer;
import com.quickwal.ledger.core.port.out.LedgerRepository;

import java.util.List;
import java.util.UUID;

/**
 * Provides the LedgerService class used by QuickWal.
 */
public class LedgerService implements LogTransfer {

    private final LedgerRepository ledgerRepository;

    /**
     * Creates a LedgerService.
     * @param ledgerRepository input value
     */
    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

        /**
     * Logs the debit and credit entries for a transfer.
     * @param transferId input value
     * @param senderWalletId input value
     * @param receiverWalletId input value
     * @param amount input value
     */

    @Override
    public void log(UUID transferId, UUID senderWalletId, UUID receiverWalletId, long amount) {
        LedgerEntry debit = new LedgerEntry(
                IdGenerator.generate(),
                transferId,
                senderWalletId,
                EntryType.DEBIT,
                amount
        );

        LedgerEntry credit = new LedgerEntry(
                IdGenerator.generate(),
                transferId,
                receiverWalletId,
                EntryType.CREDIT,
                amount
        );

        ledgerRepository.saveAll(List.of(debit, credit));
    }
}
