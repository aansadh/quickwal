package com.quickwal;

import com.quickwal.common.events.AggregateType;
import com.quickwal.transfer.core.domain.AcquisitionType;
import com.quickwal.common.money.CurrencyType;
import com.quickwal.transfer.core.domain.TransferStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EnumCodeTest {
    @Test
    void currencyTypeCodesRoundTrip() {
        assertEquals("QC", CurrencyType.QC.code());
        assertSame(CurrencyType.QC, CurrencyType.fromCode(CurrencyType.QC.code()));
    }

    @Test
    void acquisitionTypeCodesRoundTrip() {
        assertEquals("PEER2PEER", AcquisitionType.PEER2PEER.code());
        assertEquals("QRCODE", AcquisitionType.QRCODE.code());
        for (AcquisitionType value : AcquisitionType.values()) {
            assertSame(value, AcquisitionType.fromCode(value.code()));
        }
    }

    @Test
    void transferStatusCodesRoundTrip() {
        assertEquals("SUCCESSFUL", TransferStatus.SUCCESSFUL.code());
        assertEquals("FAILURE", TransferStatus.FAILURE.code());
        assertEquals("ABORTED", TransferStatus.ABORTED.code());
        assertEquals("PROCESSING", TransferStatus.PROCESSING.code());
        assertEquals("SENDER_DEBITED", TransferStatus.SENDER_DEBITED.code());
        assertEquals("REFUNDING", TransferStatus.REFUNDING.code());
        for (TransferStatus value : TransferStatus.values()) {
            assertSame(value, TransferStatus.fromCode(value.code()));
        }
    }

    @Test
    void aggregateTypeCodesRoundTrip() {
        assertEquals("TRANSFER", AggregateType.TRANSFER.code());
        assertEquals("WALLET", AggregateType.WALLET.code());
        for (AggregateType value : AggregateType.values()) {
            assertSame(value, AggregateType.fromCode(value.code()));
        }
    }

    @Test
    void unknownCodesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyType.fromCode("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> AcquisitionType.fromCode("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> TransferStatus.fromCode("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> AggregateType.fromCode("UNKNOWN"));
    }
}
