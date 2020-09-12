package io.openmarket.transaction.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import io.openmarket.transaction.model.Transaction;
import lombok.NonNull;

import javax.inject.Inject;
import java.util.Optional;

public class TransactionDaoImpl extends AbstractDynamoDBDao<Transaction> implements TransactionDao {
    @Inject
    public TransactionDaoImpl(@NonNull final AmazonDynamoDB dbClient, @NonNull final DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    public Optional<Transaction> load(@NonNull final String key) {
        return super.load(Transaction.class, key);
    }

    @Override
    protected boolean validate(final Transaction obj) {
        return true;
    }
}
