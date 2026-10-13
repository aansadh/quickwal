package com.quickwal.transfer.core.domain;

/**
 * Defines the CurrencyType enum used by QuickWal.
 */
public enum CurrencyType {

    QC("QC");

    private final String code;

    CurrencyType(String code) {
        this.code = code;
    }

    /**
     * Returns the external code.
     * @return the operation result
     */
    public String code() {
        return this.code;
    }

    /**
     * Resolves a value from its external code.
     * @param code input value
     * @return the operation result
     */
    public static CurrencyType fromCode(String code) {
        for (CurrencyType currencyType : values()) {
            if (currencyType.code.equals(code)) {
                return currencyType;
            }
        }

        throw new IllegalArgumentException("Unknown CurrencyType code: " + code);
    }
}
