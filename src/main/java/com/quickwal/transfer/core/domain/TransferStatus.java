package com.quickwal.transfer.core.domain;

/**
 * Lifecycle states of a transfer saga.
 *
 * <p>{@link #PROCESSING} is the initial state, followed by
 * {@link #SENDER_DEBITED}. A successful destination credit reaches
 * {@link #SUCCESSFUL}; debit failure reaches {@link #FAILURE}; destination
 * failure enters {@link #REFUNDING} before the final failure state.</p>
 */
public enum TransferStatus {
    SUCCESSFUL("SUCCESSFUL"),
    FAILURE("FAILURE"),
    ABORTED("ABORTED"),
    PROCESSING("PROCESSING"),
    SENDER_DEBITED("SENDER_DEBITED"),

    REFUNDING("REFUNDING");

    private final String code;

    TransferStatus(String code) {
        this.code = code;
    }

    /**
     * Returns the stable database code for this status.
     *
     * @return the persisted status code
     */
    public String code() {
        return this.code;
    }

    /**
     * Resolves a status from its stable database code.
     *
     * @param code persisted status code
     * @return the matching status
     * @throws IllegalArgumentException if {@code code} is not recognized
     */
    public static TransferStatus fromCode(String code) {
        for (TransferStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }

        throw new IllegalArgumentException("Unknown TransferStatus code: " + code);
    }
}
