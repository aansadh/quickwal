package com.quickwal.wallet.core.domain.exception;

/**
 * Provides the InsufficientFundsException class used by QuickWal.
 */
public class InsufficientFundsException extends RuntimeException {
    /**
     * Creates a InsufficientFundsException.
     * @param message input value
     */
    public InsufficientFundsException(String message) { super(message); }
}
