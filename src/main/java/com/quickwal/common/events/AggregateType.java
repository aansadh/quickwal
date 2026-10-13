package com.quickwal.common.events;

/**
 * Defines the AggregateType enum used by QuickWal.
 */
public enum AggregateType {
    TRANSFER("TRANSFER"),

    WALLET("WALLET");

    private final String code;

    AggregateType(String code) {
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
    public static AggregateType fromCode(String code) {
        for (AggregateType aggregateType : values()) {
            if (aggregateType.code.equals(code)) {
                return aggregateType;
            }
        }

        throw new IllegalArgumentException("Unknown AggregateType code: " + code);
    }
}
