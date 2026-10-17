package com.quickwal.common.port.out;

import com.quickwal.common.events.MessageConsumer;
import java.util.UUID;

/** Shared persistence port for idempotent message consumption. */
public interface Inbox {
    boolean markIfNew(UUID eventId, MessageConsumer consumer);
}
