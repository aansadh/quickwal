package com.quickwal.wallet.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;
import com.quickwal.wallet.core.domain.exception.InsufficientFundsException;
import com.quickwal.wallet.core.domain.exception.WalletNotFoundException;
import com.quickwal.wallet.core.port.out.WalletRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@OutboundAdapter
public class WalletRepositoryImpl implements WalletRepository {
    private final JdbcClient jdbcClient;

    public WalletRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    @Transactional
    public void debit(UUID walletId, Money amount) {
        ensureCurrency(walletId, amount.currency());
        int updated = jdbcClient.sql("""
                        UPDATE wallets SET balance_minor = balance_minor - :amount, version = version + 1
                        WHERE id = :id AND currency = :currency AND balance_minor >= :amount
                        """)
                .param("amount", amount.amountMinor())
                .param("id", walletId)
                .param("currency", amount.currency().code())
                .update();
        if (updated == 0) {
            if (!walletExists(walletId)) throw new WalletNotFoundException("Wallet not found: " + walletId);
            throw new InsufficientFundsException("Insufficient funds in wallet: " + walletId);
        }
    }

    @Override
    @Transactional
    public void credit(UUID walletId, Money amount) {
        addBalance(walletId, amount);
    }

    @Override
    @Transactional
    public void refund(UUID walletId, Money amount) {
        addBalance(walletId, amount);
    }

    private void addBalance(UUID walletId, Money amount) {
        ensureCurrency(walletId, amount.currency());
        int updated = jdbcClient.sql("""
                        UPDATE wallets SET balance_minor = balance_minor + :amount, version = version + 1
                        WHERE id = :id AND currency = :currency
                        """)
                .param("amount", amount.amountMinor())
                .param("id", walletId)
                .param("currency", amount.currency().code())
                .update();
        if (updated == 0) throw new WalletNotFoundException("Wallet not found: " + walletId);
    }

    private boolean walletExists(UUID walletId) {
        return jdbcClient.sql("SELECT count(*) FROM wallets WHERE id = :id")
                .param("id", walletId).query(Integer.class).single() > 0;
    }

    private void ensureCurrency(UUID walletId, CurrencyType expected) {
        String actual = jdbcClient.sql("SELECT currency FROM wallets WHERE id = :id")
                .param("id", walletId).query(String.class).optional().orElse(null);
        if (actual != null && !actual.equals(expected.code())) {
            throw new IllegalArgumentException("Wallet currency does not match command currency");
        }
    }
}

