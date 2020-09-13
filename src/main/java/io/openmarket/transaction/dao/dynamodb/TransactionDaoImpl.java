package io.openmarket.transaction.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.QueryRequest;
import com.amazonaws.services.dynamodbv2.model.QueryResult;
import com.google.common.collect.ImmutableMap;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import io.openmarket.transaction.model.Transaction;
import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static io.openmarket.config.TransactionConfig.*;

@Log4j2
public class TransactionDaoImpl extends AbstractDynamoDBDao<Transaction> implements TransactionDao {
    @Inject
    public TransactionDaoImpl(@NonNull final AmazonDynamoDB dbClient, @NonNull final DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    public Optional<Transaction> load(@NonNull final String key) {
        return super.load(Transaction.class, key);
    }

    @Override
    protected boolean validate(final Transaction transaction) {
        return transaction.getTransactionId() != null
                && !transaction.getTransactionId().isEmpty()
                && transaction.getAmount() > 0
                && transaction.getCurrencyId() != null
                && !transaction.getCurrencyId().isEmpty()
                && transaction.getPayerId() != null
                && transaction.getRecipientId() != null
                && transaction.getStatus() != null;
    }

    @Override
    public Map<String, AttributeValue> getTransactionForPayer(@NonNull final String payerId,
                                                    @NonNull final Collection<Transaction> output,
                                                    final Map<String, AttributeValue> exclusiveStartKey) {
        final QueryRequest request = new QueryRequest()
                .withTableName(TRANSACTION_DDB_TABLE_NAME)
                .withIndexName(TRANSACTION_DDB_INDEX_NAME)
                .withExpressionAttributeValues(ImmutableMap.of(":val", new AttributeValue(payerId)))
                .withKeyConditionExpression(String.format("%s = :val", TRANSACTION_DDB_ATTRIBUTE_PAYER_ID))
                .withExclusiveStartKey(exclusiveStartKey);
        final QueryResult queryResult = getDbClient().query(request);
        queryResult.getItems().forEach(a -> output.add(load(a.get(TRANSACTION_DDB_ATTRIBUTE_ID).getS()).get()));

        log.info("Loaded {} transactions for payer with Id '{}'", queryResult.getCount(), payerId);
        return queryResult.getLastEvaluatedKey();
    }
}
