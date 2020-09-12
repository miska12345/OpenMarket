package io.openmarket.transaction.model;

/**
 * The type of the transaction.
 */
public enum TransactionType {
    /**
     * TRANSFER type - one party send money to another party.
     */
    TRANSFER,

    /**
     * PAY type - one party pay the another party.
     */
    PAY
}
