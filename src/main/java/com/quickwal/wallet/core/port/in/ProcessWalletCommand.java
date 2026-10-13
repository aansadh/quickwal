package com.quickwal.wallet.core.port.in;

import com.quickwal.common.messages.command.CreditCommand;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.command.RefundCommand;

/**
 * Inbound use-case port for applying wallet commands.
 *
 * <p>Implementations perform the balance mutation and publish the resulting
 * event atomically with inbox deduplication.</p>
 */
public interface ProcessWalletCommand {
    /**
     * Processes a debit command.
     *
     * @param command command containing the wallet, transfer, and amount
     */
    void debit(DebitCommand command);
    /**
     * Processes a credit command.
     *
     * @param command command containing the wallet, transfer, and amount
     */
    void credit(CreditCommand command);
    /**
     * Processes a refund command.
     *
     * @param command command identifying the wallet and amount to restore
     */
    void refund(RefundCommand command);
}
