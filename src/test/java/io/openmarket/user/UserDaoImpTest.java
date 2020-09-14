package io.openmarket.user;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import io.openmarket.account.dao.dynamodb.UserDaoImpl;
import io.openmarket.account.model.Account;
import io.openmarket.transaction.dao.dynamodb.TransactionDaoImpl;
import io.openmarket.transaction.model.Transaction;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static io.openmarket.config.AccountConfig.USER_DDB_ATTRIBUTE_ID;
import static io.openmarket.config.AccountConfig.USER_DDB_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class UserDaoImpTest {
    private static AmazonDynamoDBLocal localDBClient;
    private AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private UserDaoImpl userDao;

    @BeforeAll
    public static void setupLocalDB() {
        localDBClient = DynamoDBEmbedded.create();
        createTable();
    }





    @Test
    public void verify() {

    }

    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    private static void createTable() {
        localDBClient.amazonDynamoDB().createTable(new CreateTableRequest().withTableName(USER_DDB_TABLE_NAME)
                .withKeySchema(new KeySchemaElement(USER_DDB_ATTRIBUTE_ID, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(USER_DDB_ATTRIBUTE_ID, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }
}
