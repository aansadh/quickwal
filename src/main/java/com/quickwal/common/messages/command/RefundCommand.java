package com.quickwal.common.messages.command;
import com.quickwal.common.messages.Command;
import com.quickwal.common.messages.MessageType;
import java.util.UUID;
/**
 * Represents the RefundCommand record used by QuickWal.
 */
import com.quickwal.common.money.Money;
public record RefundCommand(UUID eventId, UUID transferId, UUID sourceWalletId, Money amount) implements Command {
    public RefundCommand {
        amount.requirePositive();
    }
    /**
     * Returns the discriminator used for message routing.
     *
     * @return the message type
     */
    @Override public java.lang.String getEventType() { return MessageType.REFUND_COMMAND.wireName(); }
}
