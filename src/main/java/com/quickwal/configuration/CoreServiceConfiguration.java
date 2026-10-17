package com.quickwal.configuration;

import com.quickwal.common.port.out.Inbox;
import com.quickwal.common.port.out.Outbox;
import com.quickwal.common.port.out.TransactionRunner;
import com.quickwal.transfer.core.port.out.TransferRepository;
import com.quickwal.transfer.core.service.TransferService;
import com.quickwal.wallet.core.port.out.WalletRepository;
import com.quickwal.wallet.core.service.WalletService;
import com.quickwal.ledger.core.port.out.LedgerRepository;
import com.quickwal.ledger.core.service.LedgerService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreServiceConfiguration {
    @Bean
    public TransferService transferService(TransferRepository repository, Inbox inbox, Outbox outbox,
                                           TransactionRunner transactionRunner) {
        return new TransferService(repository, inbox, outbox, transactionRunner);
    }

    @Bean
    public WalletService walletService(WalletRepository repository, Inbox inbox, Outbox outbox,
                                       TransactionRunner transactionRunner) {
        return new WalletService(repository, inbox, outbox, transactionRunner);
    }

    @Bean
    public LedgerService ledgerService(LedgerRepository repository) {
        return new LedgerService(repository);
    }
}
