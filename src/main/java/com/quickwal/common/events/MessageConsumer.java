package com.quickwal.common.events;

/** Consumer identities used to deduplicate messages in the shared inbox. */
public enum MessageConsumer {
    WALLET_WORKER("wallet-worker"),
    TRANSFER_WORKER("transfer-worker");

    private final String code;
    MessageConsumer(String code) { this.code = code; }
    public String code() { return code; }
}
