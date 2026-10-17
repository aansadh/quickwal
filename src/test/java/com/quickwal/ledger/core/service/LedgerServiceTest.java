package com.quickwal.ledger.core.service;

import com.quickwal.common.money.CurrencyType;
import com.quickwal.common.money.Money;
import com.quickwal.ledger.core.domain.EntryType;
import com.quickwal.ledger.core.domain.LedgerEntry;
import com.quickwal.ledger.core.port.out.LedgerRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LedgerServiceTest {
    @Test
    void retriesProduceTheSameLogicalEntryIds() {
        LedgerRepository repository = mock(LedgerRepository.class);
        LedgerService service = new LedgerService(repository);
        UUID transferId = UUID.randomUUID();
        UUID sender = UUID.randomUUID();
        UUID receiver = UUID.randomUUID();
        Money amount = new Money(25, CurrencyType.QC);

        service.log(transferId, sender, receiver, amount);
        service.log(transferId, sender, receiver, amount);

        ArgumentCaptor<List<LedgerEntry>> entries = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).saveAll(entries.capture());
        List<LedgerEntry> first = entries.getAllValues().get(0);
        List<LedgerEntry> second = entries.getAllValues().get(1);
        assertEquals(first.stream().map(LedgerEntry::id).toList(),
                second.stream().map(LedgerEntry::id).toList());
    }

    @Test
    void requiresLedgerRepository() {
        assertThrows(NullPointerException.class, () -> new LedgerService(null));
    }
}
