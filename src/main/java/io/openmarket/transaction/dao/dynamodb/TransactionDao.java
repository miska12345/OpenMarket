package io.openmarket.transaction.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import io.openmarket.dao.dynamodb.DynamoDBDao;
import io.openmarket.transaction.model.Transaction;

import java.util.Collection;
import java.util.Map;

/**
 * TransactionDao handle transaction related DDB operations.
 */
public interface TransactionDao extends DynamoDBDao<Transaction> {
    /**
     * getTransactionForPayer get transactions with the given payerId.
     * @param payerId the payerId to get transaction for.
     * @param output the output collection.
     * @param exclusiveStartKey the exclusiveStartKey of DDB query.
     * @return the lastEvaluatedKey of the query, can be used for exclusiveStartKey to retrieve more results.
     */
    Map<String, AttributeValue> getTransactionForPayer(String payerId, Collection<Transaction> output,
                                                       Map<String, AttributeValue> exclusiveStartKey);
}
