package com.quickwal.common.messages.command;
import com.quickwal.common.messages.Command;
import java.util.UUID;
/**
 * Represents the DebitCommand record used by QuickWal.
 */
public record DebitCommand(UUID eventId, UUID transferId, UUID sourceWalletId, long amount) implements Command {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "DebitCommand"; }
}
