package com.quickwal.ledger.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.ledger.core.domain.LedgerEntry;
import com.quickwal.ledger.core.port.out.LedgerRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@OutboundAdapter
public class LedgerRepositoryImpl implements LedgerRepository {
    private final JdbcClient jdbcClient;

    public LedgerRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void saveAll(List<LedgerEntry> entries) {
        for (LedgerEntry entry : entries) {
            boolean entryTypeExists = jdbcClient.sql(
                            "SELECT count(*) FROM entry_types WHERE name = :entryType")
                    .param("entryType", entry.entryType().name())
                    .query(Long.class)
                    .single() > 0;
            if (!entryTypeExists) {
                throw new IllegalStateException(
                        "Ledger entry type is missing: " + entry.entryType());
            }
            int inserted = jdbcClient.sql("""
                    INSERT INTO ledger_entries
                      (id, transaction_id, wallet_id, entry_type_id, amount_minor, currency)
                    SELECT :id, :transferId, :walletId, id, :amount, :currency
                    FROM entry_types WHERE name = :entryType
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", entry.id())
                    .param("transferId", entry.transferId())
                    .param("walletId", entry.walletId())
                    .param("amount", entry.amount().amountMinor())
                    .param("currency", entry.amount().currency().code())
                    .param("entryType", entry.entryType().name())
                    .update();
            if (inserted > 1) throw new IllegalStateException("Ledger insert affected multiple rows");
        }
    }
}
