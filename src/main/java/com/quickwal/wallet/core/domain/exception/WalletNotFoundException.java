package com.quickwal.wallet.core.domain.exception;

/**
 * Provides the WalletNotFoundException class used by QuickWal.
 */
public class WalletNotFoundException extends RuntimeException {
    /**
     * Creates a WalletNotFoundException.
     * @param message input value
     */
    public WalletNotFoundException(String message) { super(message); }
}
