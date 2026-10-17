package com.quickwal.transfer.core.port.in;

import com.quickwal.common.messages.event.CreditCompletedEvent;
import com.quickwal.common.messages.event.CreditFailedEvent;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import com.quickwal.common.messages.event.DebitFailedEvent;
import com.quickwal.common.messages.event.RefundCompletedEvent;

/** Inbound use case for applying asynchronous wallet outcomes to a transfer. */
public interface AdvanceTransferSaga {
    void handleDebitCompleted(DebitCompletedEvent event);
    void handleCreditCompleted(CreditCompletedEvent event);
    void handleDebitFailed(DebitFailedEvent event);
    void handleCreditFailed(CreditFailedEvent event);
    void handleRefundCompleted(RefundCompletedEvent event);
}
