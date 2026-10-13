package com.quickwal.common.utils;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

/**
 * Provides the IdGenerator class used by QuickWal.
 */
public class IdGenerator {
    /**
     * Generates an identifier.
     * @return the operation result
     */
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}
