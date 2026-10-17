package com.quickwal.common.port.out;

import com.quickwal.common.events.AggregateType;
import com.quickwal.common.messages.Message;
import java.util.UUID;

/** Shared persistence port for messages emitted transactionally by aggregates. */
public interface Outbox {
    void append(AggregateType aggregateType, UUID aggregateId, Message message);
}
