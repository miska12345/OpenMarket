package io.openmarket;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDaoImpl;
import io.openmarket.organization.model.Organization;
import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static io.openmarket.config.OrgConfig.*;
import static org.junit.jupiter.api.Assertions.*;


@Log4j2
public class OrgDaoImplTest {
    private static AmazonDynamoDBLocal localDBClient;
    private static AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private OrgDaoImpl orgDao;

    @BeforeAll
    public static void setupLocalDB() {
        localDBClient = DynamoDBEmbedded.create();
    }

    @BeforeEach
    public void setup() {
        dbClient = localDBClient.amazonDynamoDB();
        dbMapper = new DynamoDBMapper(dbClient);
        orgDao = new OrgDaoImpl(dbClient, dbMapper);
        createTable();
    }

    @AfterEach
    public void cleanup() {
        dbClient.deleteTable(ORG_DDB_TABLE_NAME);
    }

    @Test
    public void when_SaveOrg_then_Exists_In_DB() {
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        orgDao.save(org);

        ScanResult result = dbClient.scan(new ScanRequest().withTableName(ORG_DDB_TABLE_NAME));
        assertEquals(1, result.getItems().size());
    }

    @Test
    public void can_Load_Org_when_Exists() {
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        orgDao.save(org);

        Optional<Organization> opOrg = orgDao.load("testOrg");
        assertTrue(opOrg.isPresent());

        Organization testOrg = opOrg.get();
        assertEquals(testOrg.getOrgName(), org.getOrgName());
        assertTrue(testOrg.getOrgCurrencies().containsAll(org.getOrgCurrencies()));
        assertEquals(testOrg.getOrgPortraitS3Key(), org.getOrgPortraitS3Key());
        assertEquals(testOrg.getOrgSlogan(), org.getOrgSlogan());
        assertEquals(testOrg.getOrgOwnerId(), org.getOrgOwnerId());
        assertEquals(testOrg.getOrgDescription(), org.getOrgDescription());
    }

    @Test
    public void cannot_Load_If_Not_Exists() {
        Optional<Organization> opTransaction = orgDao.load("123");
        assertFalse(opTransaction.isPresent());
    }

    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    private static void createTable() {
        dbClient.createTable(new CreateTableRequest().withTableName(ORG_DDB_TABLE_NAME)
                .withKeySchema(ImmutableList.of(new KeySchemaElement(ORG_DDB_ATTRIBUTE_NAME, KeyType.HASH)))
                .withAttributeDefinitions(new AttributeDefinition(ORG_DDB_ATTRIBUTE_NAME, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }
}
