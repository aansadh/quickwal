package com.quickwal.common.money;

/** Supported currencies with stable external codes. */
public enum CurrencyType {
    QC("QC", 0);

    private final String code;
    private final int minorUnitScale;
    CurrencyType(String code, int minorUnitScale) { this.code = code; this.minorUnitScale = minorUnitScale; }
    public String code() { return code; }
    public int minorUnitScale() { return minorUnitScale; }
    public static CurrencyType fromCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Currency code must not be null or blank");
        for (CurrencyType value : values()) if (value.code.equals(code)) return value;
        throw new IllegalArgumentException("Unknown currency code: " + code);
    }
}
