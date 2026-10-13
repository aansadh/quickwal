package com.quickwal.transfer.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.events.AggregateType;
import com.quickwal.common.messages.Command;
import com.quickwal.common.messages.Message;
import com.quickwal.common.utils.IdGenerator;
import com.quickwal.transfer.core.domain.*;
import com.quickwal.transfer.core.domain.exception.IdempotencyConflictException;
import com.quickwal.transfer.core.domain.exception.OptimisticLockingException;
import com.quickwal.transfer.core.port.out.TransferRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

@OutboundAdapter
/**
 * Provides the TransferRepositoryImpl class used by QuickWal.
 */
public class TransferRepositoryImpl implements TransferRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a TransferRepositoryImpl.
     * @param jdbcClient input value
     * @param objectMapper input value
     */
    public TransferRepositoryImpl(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    /**
     * Persists a transfer and its optional outbox command.
     * @param transfer input value
     * @param commandPayload input value
     * @return the operation result
     */
    public Transfer insertTransferAndOutboxEvent(Transfer transfer, Command commandPayload) {
        String insertSql = """
            INSERT INTO transactions (id, idempotency_key, sender_wallet_id, receiver_wallet_id, amount, currency, acquisition, status, msg)
            VALUES (
                :id, :idempotencyKey, :senderId, :receiverId, :amount,
                :currency, :acquisition, :status, :msg
            ) ON CONFLICT (idempotency_key) DO NOTHING
        """;

        int inserted = jdbcClient.sql(insertSql)
                .param("id", transfer.getId())
                .param("idempotencyKey", transfer.getIdempotencyKey())
                .param("senderId", transfer.getSenderWalletId())
                .param("receiverId", transfer.getReceiverWalletId())
                .param("amount", transfer.getAmount())
                .param("currency", transfer.getCurrency().code())
                .param("acquisition", transfer.getAcquisition().code())
                .param("status", transfer.getStatus().code())
                .param("msg", transfer.getMsg())
                .update();

        if (inserted == 0) {
            Transfer existing = findByIdempotencyKey(transfer.getIdempotencyKey())
                    .orElseThrow(() -> new RuntimeException("Idempotency conflict, but record not found"));

            if (!existing.getSenderWalletId().equals(transfer.getSenderWalletId()) ||
                    !existing.getReceiverWalletId().equals(transfer.getReceiverWalletId()) ||
                    existing.getAmount() != transfer.getAmount()) {

                throw new IdempotencyConflictException("Idempotency key reused with conflicting request payload");
            }
            return existing;
        }

        if (commandPayload != null) {
            saveToOutbox(AggregateType.TRANSFER, transfer.getId(), commandPayload);
        }
        return transfer;
    }

    @Override
    @Transactional
    /**
     * Persists a transfer state transition and its optional outbox command.
     * @param transfer input value
     * @param expectedPreviousStatus input value
     * @param commandPayload input value
     */
    public void updateTransferAndOutboxEvent(Transfer transfer, TransferStatus expectedPreviousStatus, Command commandPayload) {
        String updateSql = """
            UPDATE transactions
            SET status = :status, msg = :msg
            WHERE id = :id AND status = :prevStatus
        """;

        int updatedRows = jdbcClient.sql(updateSql)
                .param("status", transfer.getStatus().code())
                .param("msg", transfer.getMsg())
                .param("id", transfer.getId())
                .param("prevStatus", expectedPreviousStatus.code())
                .update();

        if (updatedRows == 0) {

            throw new OptimisticLockingException("State transition failed. Transfer modified concurrently or invalid state.");
        }

        if (commandPayload != null) {
            saveToOutbox(AggregateType.TRANSFER, transfer.getId(), commandPayload);
        }
    }

        /**
     * Finds a transfer by its identifier.
     * @param transferId input value
     * @return the operation result
     */

    @Override
    public Optional<Transfer> findById(UUID transferId) {

        return findByColumn("id", transferId);
    }

    private Optional<Transfer> findByIdempotencyKey(UUID idempotencyKey) {

        return findByColumn("t.idempotency_key", idempotencyKey);
    }

    private Optional<Transfer> findByColumn(String column, UUID value) {
        String sql = """
            SELECT t.id, t.idempotency_key, t.sender_wallet_id, t.receiver_wallet_id, t.amount, t.msg,
                   t.status, t.currency, t.acquisition
            FROM transactions t
            WHERE %s = :val
        """.formatted(column);

        return jdbcClient.sql(sql).param("val", value)
                .query((rs, rowNum) -> Transfer.hydrate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("idempotency_key", UUID.class),
                        rs.getObject("sender_wallet_id", UUID.class),
                        rs.getObject("receiver_wallet_id", UUID.class),
                        rs.getLong("amount"),
                        CurrencyType.fromCode(rs.getString("currency")),
                        AcquisitionType.fromCode(rs.getString("acquisition")),
                        TransferStatus.fromCode(rs.getString("status")),
                        rs.getString("msg")
                )).optional();
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

            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }
    }
}
