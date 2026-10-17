package com.quickwal.common.messages.command;
import com.quickwal.common.messages.Command;
import com.quickwal.common.messages.MessageType;
import java.util.UUID;
/**
 * Represents the CreditCommand record used by QuickWal.
 */
import com.quickwal.common.money.Money;
public record CreditCommand(UUID eventId, UUID transferId, UUID targetWalletId, Money amount) implements Command {
    public CreditCommand {
        amount.requirePositive();
    }
    /**
     * Returns the discriminator used for message routing.
     *
     * @return the message type
     */
    @Override public java.lang.String getEventType() { return MessageType.CREDIT_COMMAND.wireName(); }
}
