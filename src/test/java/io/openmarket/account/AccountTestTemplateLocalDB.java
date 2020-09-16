package io.openmarket.account;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import io.openmarket.account.dynamodb.UserDao;
import io.openmarket.account.dynamodb.UserDaoImpl;
import io.openmarket.account.service.AccountServiceHandler;
import io.openmarket.account.service.CredentialManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import static io.openmarket.config.AccountConfig.USER_DDB_ATTRIBUTE_USERNAME;
import static io.openmarket.config.AccountConfig.USER_DDB_TABLE_NAME;

public class AccountTestTemplateLocalDB {
    protected static AmazonDynamoDBLocal localDBClient;
    protected AmazonDynamoDB dbClient;
    protected DynamoDBMapper dbMapper;
    protected UserDao userDao;
    protected AccountServiceHandler ash;

    @BeforeAll
    public static void setupLocalDB() {
        localDBClient = DynamoDBEmbedded.create();
    }

    @BeforeEach
    public void setup() {
        this.dbClient = this.localDBClient.amazonDynamoDB();
        this.dbMapper = new DynamoDBMapper(dbClient);
        this.userDao = new UserDaoImpl(dbClient, dbMapper);
        this.ash = new AccountServiceHandler(userDao, new CredentialManager(false));
    }

    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    @AfterEach
    public void deleteTalbe() {
        dbClient.deleteTable(new DeleteTableRequest().withTableName(USER_DDB_TABLE_NAME));
    }

    @BeforeEach
    private void createTable() {
        localDBClient.amazonDynamoDB().createTable(new CreateTableRequest().withTableName(USER_DDB_TABLE_NAME)
                .withKeySchema(new KeySchemaElement(USER_DDB_ATTRIBUTE_USERNAME, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(USER_DDB_ATTRIBUTE_USERNAME, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }
}
