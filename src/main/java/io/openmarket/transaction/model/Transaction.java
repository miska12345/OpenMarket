package io.openmarket.transaction.model;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

import static io.openmarket.config.TransactionConfig.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDBTable(tableName = TRANSACTION_DDB_TABLE_NAME)
public class Transaction {
    @DynamoDBHashKey(attributeName = TRANSACTION_DDB_ATTRIBUTE_ID)
    @DynamoDBAutoGeneratedKey
    private String transactionId;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_TYPE)
    private TransactionType type;

    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_PAYER_ID)
    private String payerId;

    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_RECIPIENT_ID)
    private String recipientId;

    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_CURRENCY_ID)
    private String currencyId;

    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_AMOUNT)
    private Double amount;

    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_NOTE)
    private String note;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_STATUS)
    private TransactionStatus status;

    @DynamoDBTypeConvertedEnum
    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_ERROR_STATUS)
    private TransactionErrorType error;

    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.CREATE)
    @DynamoDBTypeConvertedTimestamp
    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_CREATED_AT)
    private Date createdAt;

    @DynamoDBAutoGeneratedTimestamp(strategy = DynamoDBAutoGenerateStrategy.ALWAYS)
    @DynamoDBTypeConvertedTimestamp
    @DynamoDBAttribute(attributeName = TRANSACTION_DDB_ATTRIBUTE_UPDATED_AT)
    private Date lastUpdatedAt;
}