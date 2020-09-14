package io.openmarket.account.dao.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import io.openmarket.account.model.Account;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static io.openmarket.config.AccountConfig.*;

public class UserDaoImplTest {
    private static AmazonDynamoDBLocal localDBClient;
    private AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private UserDaoImpl userDao;

    @BeforeAll
    public static void setupLocalDB() {
        localDBClient = DynamoDBEmbedded.create();
    }

    @BeforeEach
    public void setup() {
        dbClient = localDBClient.amazonDynamoDB();
        dbMapper = new DynamoDBMapper(dbClient);
        userDao = new UserDaoImpl(dbClient, dbMapper);
    }


    @AfterEach
    public void deleteTalbe() {
        dbClient.deleteTable(new DeleteTableRequest().withTableName(USER_DDB_TABLE_NAME));
    }

    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    @BeforeEach
    private void createTable() {
        localDBClient.amazonDynamoDB().createTable(new CreateTableRequest().withTableName(USER_DDB_TABLE_NAME)
                .withKeySchema(new KeySchemaElement(USER_DDB_ATTRIBUTE_USERNAME, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(USER_DDB_ATTRIBUTE_USERNAME, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }

}

