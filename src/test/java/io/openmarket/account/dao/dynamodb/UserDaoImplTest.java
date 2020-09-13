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
        createTable();
    }

    @BeforeEach
    public void setup() {
        dbClient = localDBClient.amazonDynamoDB();
        dbMapper = new DynamoDBMapper(dbClient);
        userDao = new UserDaoImpl(dbClient, dbMapper);
    }
    @Test
    public void when_User_Exist_then_getUser() {
        Account newUser = Account.builder().displayName("weifeng1").username("weifeng")
                .createAt(Instant.now().toString()).build();
        this.userDao.save(newUser);
        Optional<Account> user1 = this.userDao.getUser("weifeng", "username");
        assertTrue(user1.isPresent());
        assertEquals("weifeng", user1.get().getUsername());
    }

    @Test
    public void cannot_getUser_when_User_Not_Exist() {
        Optional<Account> user2 = userDao.getUser("weifeng1", "username");
        assertFalse(user2.isPresent());
    }

    @Test
    public void can_getUser_Correctly_when_User_Exist () {
        Account newUser = Account.builder().displayName("didntpay").username("weifeng1")
                .createAt(Instant.now().toString()).build();

        this.userDao.save(newUser);

        Optional<Account> user1 = this.userDao.getUser("weifeng1", "username, " + USER_DDB_ATTRIBUTE_DISPLAYNAME);
        assertTrue(user1.isPresent());
        assertEquals("weifeng1", user1.get().getUsername());
        assertEquals("didntpay", user1.get().getDisplayName());


    }
    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    private static void createTable() {

        localDBClient.amazonDynamoDB().createTable(new CreateTableRequest().withTableName(USER_DDB_TABLE_NAME)
                .withKeySchema(new KeySchemaElement(USER_DDB_ATTRIBUTE_USERNAME, KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition(USER_DDB_ATTRIBUTE_USERNAME, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }

}

