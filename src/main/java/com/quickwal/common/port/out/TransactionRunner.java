package com.quickwal.common.port.out;

import java.util.function.Supplier;

/** Runs application work in one persistence transaction. */
public interface TransactionRunner {
    <T> T inTransaction(Supplier<T> work);

    default void inTransaction(Runnable work) {
        inTransaction(() -> { work.run(); return null; });
    }
}
