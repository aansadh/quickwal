package com.quickwal.transfer.core.domain.exception;

/**
 * Provides the TransferNotFoundException class used by QuickWal.
 */
public class TransferNotFoundException extends RuntimeException {
    /**
     * Creates a TransferNotFoundException.
     * @param message input value
     */
    public TransferNotFoundException(String message) { super(message); }
}
