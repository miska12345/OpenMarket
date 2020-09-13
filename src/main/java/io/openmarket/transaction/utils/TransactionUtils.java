package io.openmarket.transaction.utils;

import java.util.UUID;

public final class TransactionUtils {
    private TransactionUtils() {}

    /**
     * Get a unique transaction ID.
     * @return a unique transaction ID.
     */
    public static String generateTransactionID() {
        return UUID.randomUUID().toString();
    }
}
