package com.quickwal.transfer.core.port.in;

import com.quickwal.common.money.Money;
import java.util.UUID;

/**
 * Inbound use-case port for starting a transfer saga.
 */
public interface InitiateTransfer {
    /**
     * Creates a transfer in its initial state and emits the debit command
     * with its debit command through the transactional outbox.
     *
     * @param sourceWalletId wallet from which funds will be debited
     * @param targetWalletId wallet to which funds will be credited
     * @param amount positive transfer amount
     * @param idempotencyKey UUID string supplied by the caller to prevent
     * duplicate transfer creation
     * @return the persisted transfer identifier
     * @throws IllegalArgumentException if the idempotency key is not a UUID or
     * the transfer amount is not positive
     */
    UUID execute(UUID sourceWalletId, UUID targetWalletId, Money amount, String idempotencyKey);
}

