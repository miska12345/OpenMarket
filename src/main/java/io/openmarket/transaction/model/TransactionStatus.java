package io.openmarket.transaction.model;

/**
 * The status for transaction.
 */
public enum TransactionStatus {
    /**
     * PENDING - transaction is not done processing.
     */
    PENDING,

    /**
     * CONFIRMED - transaction has been finalized.
     */
    CONFIRMED,

    /**
     * ERROR - transaction error.
     */
    ERROR,

    /**
     * CANCELLED - transaction not finalized.
     */
    CANCELED
}
