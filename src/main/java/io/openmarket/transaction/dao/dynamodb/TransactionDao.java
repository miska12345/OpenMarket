package io.openmarket.transaction.dao;

import io.openmarket.dao.dynamodb.DynamoDBDao;
import io.openmarket.transaction.model.Transaction;

public interface TransactionDao extends DynamoDBDao<Transaction> {
}
