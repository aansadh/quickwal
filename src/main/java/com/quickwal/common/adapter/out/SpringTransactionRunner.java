package com.quickwal.common.adapter.out;

import com.quickwal.common.architecture.OutboundAdapter;
import com.quickwal.common.port.out.TransactionRunner;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.function.Supplier;

@OutboundAdapter
public class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        return transactionTemplate.execute(status -> work.get());
    }
}
