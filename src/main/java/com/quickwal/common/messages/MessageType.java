package com.quickwal.common.messages;

/** Stable message type identifiers used on the Kafka wire. */
public enum MessageType {
    DEBIT_COMMAND("DebitCommand"),
    CREDIT_COMMAND("CreditCommand"),
    REFUND_COMMAND("RefundCommand"),
    DEBIT_COMPLETED_EVENT("DebitCompletedEvent"),
    DEBIT_FAILED_EVENT("DebitFailedEvent"),
    CREDIT_COMPLETED_EVENT("CreditCompletedEvent"),
    CREDIT_FAILED_EVENT("CreditFailedEvent"),
    REFUND_COMPLETED_EVENT("RefundCompletedEvent");

    private final String wireName;

    MessageType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static MessageType fromWireName(String wireName) {
        if (wireName == null || wireName.isBlank()) {
            throw new IllegalArgumentException("Message type must not be null or blank");
        }
        for (MessageType type : values()) {
            if (type.wireName.equals(wireName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported message type: " + wireName);
    }
}
