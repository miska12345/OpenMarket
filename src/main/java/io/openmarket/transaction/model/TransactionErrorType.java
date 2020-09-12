package io.openmarket.transaction.model;

/**
 * TransactionErrorType indicates the reason why Transaction is error.
 */
public enum TransactionErrorType {
    /**
     * NONE means the current transaction is not in error.
     */
    NONE,

    /**
     * INVALID_PAYMENT_AMOUNT means the payer doesn't have enough balance.
     */
    INSUFFICIENT_PAYMENT_AMOUNT,
}
