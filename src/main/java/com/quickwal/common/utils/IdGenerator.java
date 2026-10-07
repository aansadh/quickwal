package com.quickwal.common.utils;

import com.github.f4b6a3.uuid.UuidCreator;
import java.util.UUID;

public class IdGenerator {
    public static UUID generate() {
        return UuidCreator.getTimeOrderedEpoch();
    }
}