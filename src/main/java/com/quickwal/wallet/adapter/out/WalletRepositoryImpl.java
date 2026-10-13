package com.quickwal.wallet.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.events.AggregateType;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.Message;
import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.wallet.core.domain.exception.InsufficientFundsException;
import com.quickwal.wallet.core.domain.exception.WalletNotFoundException;
import com.quickwal.wallet.core.port.out.WalletRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@OutboundAdapter
/**
 * Provides the WalletRepositoryImpl class used by QuickWal.
 */
public class WalletRepositoryImpl implements WalletRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a WalletRepositoryImpl.
     * @param jdbcClient input value
     * @param objectMapper input value
     */
    public WalletRepositoryImpl(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    /**
     * Deducts funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    public void deductAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, DebitCompletedEvent event) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be strictly positive");
        if (isDuplicate(eventId)) return;

        int updatedRows = jdbcClient.sql("UPDATE wallets SET balance = balance - :amount, version = version + 1 WHERE id = :id AND balance >= :amount")
                .param("amount", amount)
                .param("id", walletId)
                .update();

        if (updatedRows == 0) {
            if (!walletExists(walletId)) throw new WalletNotFoundException("Wallet not found: " + walletId);

            throw new InsufficientFundsException("Insufficient funds in wallet: " + walletId);
        }


        saveToOutbox(AggregateType.WALLET, walletId, event);
    }

    @Override
    @Transactional
    /**
     * Adds funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    public void addAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, CreditCompletedEvent event) {

        updateBalanceAndSaveEvent(walletId, amount, eventId, event);
    }

    @Override
    @Transactional
    /**
     * Refunds funds and persists the resulting outbox event.
     * @param walletId input value
     * @param amount input value
     * @param eventId input value
     * @param event input value
     */
    public void refundAndSaveOutboxEvent(UUID walletId, long amount, UUID eventId, RefundCompletedEvent event) {

        updateBalanceAndSaveEvent(walletId, amount, eventId, event);
    }

    private void updateBalanceAndSaveEvent(UUID walletId, long amount, UUID eventId, Event event) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be strictly positive");
        if (isDuplicate(eventId)) return;

        int updatedRows = jdbcClient.sql("UPDATE wallets SET balance = balance + :amount, version = version + 1 WHERE id = :id")
                .param("amount", amount)
                .param("id", walletId)
                .update();

        if (updatedRows == 0) throw new WalletNotFoundException("Wallet not found: " + walletId);


        saveToOutbox(AggregateType.WALLET, walletId, event);
    }

    @Override
    @Transactional
    /**
     * Persists a failure event in the outbox.
     * @param walletId input value
     * @param event input value
     */
    public void saveFailureOutboxEvent(UUID walletId, Event event) {

        saveToOutbox(AggregateType.WALLET, walletId, event);
    }

    private boolean isDuplicate(UUID eventId) {
        int inserted = jdbcClient.sql("INSERT INTO inbox_events (event_id, consumer_name) VALUES (:eventId, 'wallet-worker') ON CONFLICT (event_id, consumer_name) DO NOTHING")
                .param("eventId", eventId)
                .update();
        return inserted == 0;
    }

    private boolean walletExists(UUID walletId) {
        return jdbcClient.sql("SELECT count(*) FROM wallets WHERE id = :id")
                .param("id", walletId)
                .query(Integer.class)
                .single() > 0;
    }

    private void saveToOutbox(AggregateType aggregateType, UUID aggregateId, Message payload) {
        try {
            jdbcClient.sql("INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload) VALUES (:id, :type, :aggId, :evtType, CAST(:payload AS jsonb))")
                    .param("id", IdGenerator.generate())
                    .param("type", aggregateType.code())
                    .param("aggId", aggregateId)
                    .param("evtType", payload.getEventType())
                    .param("payload", objectMapper.writeValueAsString(payload))
                    .update();
        } catch (Exception e) {

            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
