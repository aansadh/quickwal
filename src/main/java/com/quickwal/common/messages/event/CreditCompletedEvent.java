package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.MessageType;
import java.util.UUID;
import com.quickwal.common.money.CurrencyType;
/**
 * Represents the CreditCompletedEvent record used by QuickWal.
 */
public record CreditCompletedEvent(UUID eventId, UUID transferId, UUID targetWalletId, CurrencyType currency) implements Event {
    /**
     * Returns the discriminator used for message routing.
     *
     * @return the message type
     */
    @Override public java.lang.String getEventType() { return MessageType.CREDIT_COMPLETED_EVENT.wireName(); }
}
