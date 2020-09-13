package io.openmarket.config;

import io.openmarket.transaction.model.TransactionErrorType;
import io.openmarket.transaction.model.TransactionStatus;

public final class TransactionConfig {
    private TransactionConfig() {}

    /**
     * ------------------------------------------------------
     * DDB Configurations.
     * ------------------------------------------------------
     */

    /**
     * The DDB table name for Transaction service.
     */
    public static final String TRANSACTION_DDB_TABLE_NAME = "Transaction";

    /**
     * The DDB attribute name for unique Id of the transaction.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_ID = "TransactionId";

    /**
     * The DDB attribute name for transaction type.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_TYPE = "Type";

    /**
     * The DDB attribute name for the currency that is used in the transaction.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_CURRENCY_ID = "CurrencyId";

    /**
     * The DDB attribute name for the amount of currency in this transaction.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_AMOUNT = "Amount";

    /**
     * The DDB attribute name for payer's unique Id.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_PAYER_ID = "PayerId";

    /**
     * The DDB attribute name for for recipient's unique Id.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_RECIPIENT_ID = "RecipientId";

    /**
     * The DDB attribute name for the note that the payer left for the recipient.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_NOTE = "Note";

    /**
     * The DDB attribute name for the status of this transaction.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_STATUS = "Status";

    /**
     * The DDB attribute name for error status.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_ERROR_STATUS = "ErrorStatus";

    /**
     * The DDB attribute name for the timestamp of when this transaction was created.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_CREATED_AT = "CreatedAt";

    /**
     * The DDB attribute name for for the timestamp of when this transaction was last modified.
     */
    public static final String TRANSACTION_DDB_ATTRIBUTE_UPDATED_AT = "UpdatedAt";

    /**
     * The DDB index name for payerId to transactionIds.
     */
    public static final String TRANSACTION_DDB_INDEX_NAME = String.format("%s-%s-index",
            TRANSACTION_DDB_ATTRIBUTE_PAYER_ID, TRANSACTION_DDB_ATTRIBUTE_CREATED_AT);

    /**
     * ------------------------------------------------------
     * Transaction Initial Values.
     * ------------------------------------------------------
     */

    /**
     * The default transaction status for new transactions.
     */
    public static TransactionStatus TRANSACTION_INITIAL_STATUS = TransactionStatus.PENDING;

    /**
     * The default transaction error status for new transactions.
     */
    public static TransactionErrorType TRANSACTION_INITIAL_ERROR_TYPE = TransactionErrorType.NONE;
}
