package com.quickwal.wallet.core.port.out;

import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;
import com.quickwal.common.messages.Event;
import java.util.UUID;

/**
 * Defines the WalletRepository interface used by QuickWal.
 */
public interface WalletRepository {
    /**
     * Deducts funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    void deductAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, DebitCompletedEvent event);
    /**
     * Adds funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    void addAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, CreditCompletedEvent event);
    /**
     * Refunds funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    void refundAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, RefundCompletedEvent event);
    /**
     * Persists a failure event in the outbox.
     * @param walletId input value
     * @param event input value
     */
    void saveFailureOutboxEvent(UUID walletId, Event event);
}
