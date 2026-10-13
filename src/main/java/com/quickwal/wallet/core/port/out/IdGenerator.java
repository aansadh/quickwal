package com.quickwal.wallet.core.port.out;

import java.util.UUID;

/**
 * Defines the IdGenerator interface used by QuickWal.
 */
public interface IdGenerator {
    /**
     * Performs the generateId operation.
     * @return the operation result
     */
    UUID generateId();
}
