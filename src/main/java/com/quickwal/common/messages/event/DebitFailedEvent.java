package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import java.util.UUID;
/**
 * Represents the DebitFailedEvent record used by QuickWal.
 */
public record DebitFailedEvent(UUID eventId, UUID transferId, UUID sourceWalletId, String reason) implements Event {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "DebitFailedEvent"; }
}
