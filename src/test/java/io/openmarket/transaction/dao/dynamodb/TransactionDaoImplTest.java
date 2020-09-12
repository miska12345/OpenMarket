package io.openmarket.transaction.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import io.openmarket.transaction.model.Transaction;
import io.openmarket.transaction.model.TransactionStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.openmarket.config.TransactionConfig.TRANSACTION_DDB_ATTRIBUTE_ID;
import static io.openmarket.config.TransactionConfig.TRANSACTION_DDB_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.*;

public class TransactionDaoImplTest {
    private static AmazonDynamoDBLocal localDBClient;
    private AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private TransactionDaoImpl transactionDao;

    @BeforeAll
    public static void setupLocalDB() {
        localDBClient = DynamoDBEmbedded.create();
        createTable();
    }

    @BeforeEach
    public void setup() {
        dbClient = localDBClient.amazonDynamoDB();
        dbMapper = new DynamoDBMapper(dbClient);
        transactionDao = new TransactionDaoImpl(dbClient, dbMapper);
    }

    @Test
    public void when_SaveTransaction_then_Exists_In_DB() {
        Transaction transaction = Transaction.builder().transactionId("123").payerId("123").status(TransactionStatus.PENDING)
                .recipientId("123").amount(3.14).currencyId("123").build();
        transactionDao.save(transaction);

        ScanResult result = dbClient.scan(new ScanRequest().withTableName(TRANSACTION_DDB_TABLE_NAME));
        assertEquals(1, result.getItems().size());
    }

    @Test
    public void can_Load_Transaction_when_Exists() {
        Transaction transaction = Transaction.builder().transactionId("123").payerId("123").status(TransactionStatus.PENDING)
                .recipientId("123").amount(3.14).currencyId("123").build();
        transactionDao.save(transaction);

        Optional<Transaction> opTransaction = transactionDao.load("123");
        assertTrue(opTransaction.isPresent());

        Transaction transaction1 = opTransaction.get();
        assertEquals(transaction.getTransactionId(), transaction1.getTransactionId());
        assertEquals(transaction.getCurrencyId(), transaction1.getCurrencyId());
        assertEquals(transaction.getAmount(), transaction1.getAmount());
        assertEquals(transaction.getPayerId(), transaction1.getPayerId());
        assertEquals(transaction.getStatus(), transaction1.getStatus());
    }

    @Test
    public void cannot_Load_If_Not_Exists() {
        Optional<Transaction> opTransaction = transactionDao.load("123");
        assertFalse(opTransaction.isPresent());
    }

    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    private static void createTable() {
        localDBClient.amazonDynamoDB().createTable(new CreateTableRequest().withTableName(TRANSACTION_DDB_TABLE_NAME)
                .withKeySchema(ImmutableList.of(new KeySchemaElement(TRANSACTION_DDB_ATTRIBUTE_ID, KeyType.HASH)))
                .withAttributeDefinitions(new AttributeDefinition(TRANSACTION_DDB_ATTRIBUTE_ID, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }
}
