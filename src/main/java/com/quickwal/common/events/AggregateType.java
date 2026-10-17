package com.quickwal.common.events;

public enum AggregateType {
    TRANSFER("TRANSFER"),
    WALLET("WALLET");

    private final String value;

    AggregateType(String value) {
        this.value = value;
    }

    public String code() {
        return value;
    }

    public static AggregateType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Aggregate type code must not be null or blank");
        }
        for (AggregateType aggregateType : values()) {
            if (aggregateType.value.equals(code)) {
                return aggregateType;
            }
        }
        throw new IllegalArgumentException("Unknown AggregateType code: " + code);
    }

    @Override
    public String toString() {
        return code();
    }
}
