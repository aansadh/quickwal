package com.quickwal.common.messages;

import java.util.UUID;

/**
 * Common contract for messages serialized into the transactional outbox.
 *
 * <p>The event identifier identifies the message instance, while the event
 * type is the stable routing/persistence identifier used by downstream
 * consumers. Implementations must keep the event type compatible with the
 * existing messaging contract.</p>
 */
public interface Message {
    /**
     * Returns the identifier of this message instance.
     *
     * @return the message identifier
     */
    UUID eventId();

    /**
     * Returns the stable message type used for persistence and routing.
     *
     * @return the message type identifier
     */
    String getEventType();
}
