package com.quickwal.common.messages.event;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.MessageType;
import java.util.UUID;
import com.quickwal.common.money.CurrencyType;
import java.lang.String;
/**
 * Represents the DebitCompletedEvent record used by QuickWal.
 */
public record DebitCompletedEvent(UUID eventId, UUID transferId, UUID sourceWalletId, CurrencyType currency) implements Event {
    /**
     * Returns the discriminator used for message routing.
     *
     * @return the message type
     */
    @Override public java.lang.String getEventType() { return MessageType.DEBIT_COMPLETED_EVENT.wireName(); }
}
