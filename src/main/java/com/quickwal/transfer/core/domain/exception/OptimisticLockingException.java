package com.quickwal.transfer.core.domain.exception;

/**
 * Provides the OptimisticLockingException class used by QuickWal.
 */
public class OptimisticLockingException extends RuntimeException {
    /**
     * Creates a OptimisticLockingException.
     * @param message input value
     */
    public OptimisticLockingException(String message) { super(message); }
}
