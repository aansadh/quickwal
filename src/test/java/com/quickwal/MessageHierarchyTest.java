package com.quickwal;

import com.quickwal.common.messages.Command;
import com.quickwal.common.messages.Event;
import com.quickwal.common.messages.command.DebitCommand;
import com.quickwal.common.messages.event.DebitCompletedEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MessageHierarchyTest {
    @Test
    void commandsAndEventsHaveDistinctTypedContracts() {
        UUID eventId = UUID.randomUUID();
        DebitCommand command = new DebitCommand(eventId, UUID.randomUUID(), UUID.randomUUID(), new Money(10, CurrencyType.QC));
        DebitCompletedEvent event = new DebitCompletedEvent(eventId, UUID.randomUUID(), UUID.randomUUID(), CurrencyType.QC);

        assertInstanceOf(Command.class, command);
        assertInstanceOf(Event.class, event);
        assertEquals("DebitCommand", command.getEventType());
        assertEquals("DebitCompletedEvent", event.getEventType());
    }
}
