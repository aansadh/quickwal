package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import java.util.UUID;
/**
 * Represents the CreditCompletedEvent record used by QuickWal.
 */
public record CreditCompletedEvent(UUID eventId, UUID transferId, UUID targetWalletId) implements Event {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "CreditCompletedEvent"; }
}
