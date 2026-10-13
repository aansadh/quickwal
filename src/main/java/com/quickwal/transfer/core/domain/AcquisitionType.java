package com.quickwal.transfer.core.domain;

/**
 * Defines the AcquisitionType enum used by QuickWal.
 */
public enum AcquisitionType {
    PEER2PEER("PEER2PEER"),

    QRCODE("QRCODE");

    private final String code;

    AcquisitionType(String code) {
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
    public static AcquisitionType fromCode(String code) {
        for (AcquisitionType acquisitionType : values()) {
            if (acquisitionType.code.equals(code)) {
                return acquisitionType;
            }
        }

        throw new IllegalArgumentException("Unknown AcquisitionType code: " + code);
    }
}
