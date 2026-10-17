package com.quickwal.transfer.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.money.CurrencyType;
import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.transfer.core.domain.Transfer;
import com.quickwal.transfer.core.domain.TransferStatus;
import com.quickwal.transfer.core.domain.exception.IdempotencyConflictException;
import com.quickwal.transfer.core.domain.exception.OptimisticLockingException;
import com.quickwal.transfer.core.port.out.TransferRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@OutboundAdapter
public class PostgresTransferRepository implements TransferRepository {
    private final JdbcClient jdbcClient;

    public PostgresTransferRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public Transfer insert(Transfer transfer) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO transactions (id, idempotency_key, sender_wallet_id, receiver_wallet_id,
                                                 amount_minor, currency, acquisition, status, msg)
                        VALUES (:id, :idempotencyKey, :senderId, :receiverId, :amountMinor,
                                :currency, :acquisition, :status, :msg)
                        ON CONFLICT (idempotency_key) DO NOTHING
                        """)
                .param("id", transfer.getId())
                .param("idempotencyKey", transfer.getIdempotencyKey())
                .param("senderId", transfer.getSenderWalletId())
                .param("receiverId", transfer.getReceiverWalletId())
                .param("amountMinor", transfer.getAmount().amountMinor())
                .param("currency", transfer.getAmount().currency().code())
                .param("acquisition", transfer.getAcquisition().code())
                .param("status", transfer.getStatus().code())
                .param("msg", transfer.getMsg())
                .update();

        if (inserted == 1) return transfer;
        Transfer existing = findByColumn("t.idempotency_key", transfer.getIdempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Idempotency conflict, but record not found"));
        if (!existing.getSenderWalletId().equals(transfer.getSenderWalletId())
                || !existing.getReceiverWalletId().equals(transfer.getReceiverWalletId())
                || !existing.getAmount().equals(transfer.getAmount())
                || existing.getAcquisition() != transfer.getAcquisition()) {
            throw new IdempotencyConflictException("Idempotency key reused with conflicting request payload");
        }
        return existing;
    }

    @Override
    @Transactional
    public void update(Transfer transfer, TransferStatus expectedPreviousStatus) {
        int updatedRows = jdbcClient.sql("""
                        UPDATE transactions SET status = :status, msg = :msg
                        WHERE id = :id AND status = :previousStatus
                        """)
                .param("status", transfer.getStatus().code())
                .param("msg", transfer.getMsg())
                .param("id", transfer.getId())
                .param("previousStatus", expectedPreviousStatus.code())
                .update();
        if (updatedRows == 0) {
            throw new OptimisticLockingException("State transition failed. Transfer modified concurrently or invalid state.");
        }
    }

    @Override
    public Optional<Transfer> findById(UUID transferId) {
        return findByColumn("t.id", transferId);
    }

    private Optional<Transfer> findByColumn(String column, UUID value) {
        String sql = """
                SELECT t.id, t.idempotency_key, t.sender_wallet_id, t.receiver_wallet_id, t.amount_minor,
                       t.msg, t.status, t.currency, t.acquisition
                FROM transactions t WHERE %s = :value
                """.formatted(column);
        return jdbcClient.sql(sql).param("value", value)
                .query((rs, rowNum) -> Transfer.hydrate(
                        rs.getObject("id", UUID.class),
                        rs.getObject("idempotency_key", UUID.class),
                        rs.getObject("sender_wallet_id", UUID.class),
                        rs.getObject("receiver_wallet_id", UUID.class),
                        new com.quickwal.common.money.Money(rs.getLong("amount_minor"),
                                CurrencyType.fromCode(rs.getString("currency"))),
                        AcquisitionType.fromCode(rs.getString("acquisition")),
                        TransferStatus.fromCode(rs.getString("status")),
                        rs.getString("msg"))).optional();
    }
}
