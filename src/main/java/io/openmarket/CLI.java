package io.openmarket;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import io.openmarket.transaction.dao.TransactionDao;
import io.openmarket.transaction.dao.TransactionDaoImpl;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionStatus;

public class CLI {
    public static void main(String[] args) {
        AmazonDynamoDB dbClient = AmazonDynamoDBClientBuilder.standard().build();
        TransactionDao dao = new TransactionDaoImpl(dbClient, new DynamoDBMapper(dbClient));
        dao.save(Transaction.builder().payerId("123").recipientId("321").currencyId("222").amount("123").status(TransactionStatus.PENDING).build());
    }
}
