package com.quickwal.transfer.core.domain.exception;

/**
 * Provides the IdempotencyConflictException class used by QuickWal.
 */
public class IdempotencyConflictException extends RuntimeException {
    /**
     * Creates a IdempotencyConflictException.
     * @param message input value
     */
    public IdempotencyConflictException(String message) { super(message); }
}
