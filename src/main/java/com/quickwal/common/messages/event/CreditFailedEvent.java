package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import java.util.UUID;
/**
 * Represents the CreditFailedEvent record used by QuickWal.
 */
public record CreditFailedEvent(UUID eventId, UUID transferId, UUID targetWalletId, String reason) implements Event {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "CreditFailedEvent"; }
}
