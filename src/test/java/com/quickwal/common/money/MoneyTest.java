package com.quickwal.common.money;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void supportsZeroAndNegativeValues() {
        assertTrue(new Money(0, CurrencyType.QC).isZero());
        assertTrue(new Money(-1, CurrencyType.QC).isNegative());
    }

    @Test
    void rejectsNullCurrency() {
        assertThrows(NullPointerException.class, () -> new Money(1, null));
    }

    @Test
    void addsAndSubtractsWithCheckedArithmetic() {
        Money amount = new Money(10, CurrencyType.QC);
        assertEquals(new Money(15, CurrencyType.QC), amount.add(new Money(5, CurrencyType.QC)));
        assertEquals(new Money(5, CurrencyType.QC), amount.subtract(new Money(5, CurrencyType.QC)));
        assertThrows(ArithmeticException.class,
                () -> new Money(Long.MAX_VALUE, CurrencyType.QC).add(new Money(1, CurrencyType.QC)));
    }

    @Test
    void exposesScaleMetadata() {
        assertEquals(0, CurrencyType.QC.minorUnitScale());
    }
}
