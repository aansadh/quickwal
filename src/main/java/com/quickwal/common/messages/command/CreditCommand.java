package com.quickwal.common.messages.command;
import com.quickwal.common.messages.Command;
import java.util.UUID;
/**
 * Represents the CreditCommand record used by QuickWal.
 */
public record CreditCommand(UUID eventId, UUID transferId, UUID targetWalletId, long amount) implements Command {
    /**
     * Returns the discriminator used for message routing.
     * @return the message type
     */
    @Override public String getEventType() { return "CreditCommand"; }
}
