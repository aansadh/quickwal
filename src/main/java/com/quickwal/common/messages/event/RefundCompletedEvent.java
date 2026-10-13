package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import java.util.UUID;
/**
 * Represents the RefundCompletedEvent record used by QuickWal.
 */
public record RefundCompletedEvent(UUID eventId, UUID transferId, UUID sourceWalletId) implements Event {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "RefundCompletedEvent"; }
}
