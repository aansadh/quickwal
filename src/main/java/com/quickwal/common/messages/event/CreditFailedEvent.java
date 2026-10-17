package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.MessageType;
import java.util.UUID;
import com.quickwal.common.money.CurrencyType;
/**
 * Represents the CreditFailedEvent record used by QuickWal.
 */
public record CreditFailedEvent(UUID eventId, UUID transferId, UUID targetWalletId, String reason, CurrencyType currency) implements Event {
    /**
     * Returns the discriminator used for message routing.
     *
     * @return the message type
     */
    @Override public String getEventType() { return MessageType.CREDIT_FAILED_EVENT.wireName(); }
}
