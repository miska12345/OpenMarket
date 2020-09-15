package io.openmarket;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDao;
import io.openmarket.organization.dao.OrgDaoImpl;
import io.openmarket.organization.model.Organization;
import io.openmarket.organization.service.OrgServiceHandler;
import org.junit.jupiter.api.*;

import java.util.Optional;

import static io.openmarket.config.OrgConfig.ORG_DDB_ATTRIBUTE_NAME;
import static io.openmarket.config.OrgConfig.ORG_DDB_TABLE_NAME;
import static org.junit.jupiter.api.Assertions.*;

public class OrgServiceHandlerTest {
    private static AmazonDynamoDBLocal localDBClient;
    private static AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private OrgDaoImpl orgDao;
    private OrgServiceHandler serviceHandler;

    private static void createTable() {
        dbClient.createTable(new CreateTableRequest().withTableName(ORG_DDB_TABLE_NAME)
                .withKeySchema(ImmutableList.of(new KeySchemaElement(ORG_DDB_ATTRIBUTE_NAME, KeyType.HASH)))
                .withAttributeDefinitions(new AttributeDefinition(ORG_DDB_ATTRIBUTE_NAME, ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput(5L, 5L)));
    }

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
        serviceHandler = new OrgServiceHandler(orgDao);
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
        serviceHandler.addOrg(org);
        ScanResult result = dbClient.scan(new ScanRequest().withTableName(ORG_DDB_TABLE_NAME));
        assertEquals(1, result.getItems().size());
    }

    @Test
    public void saveOrg_then_getOrg_by_name() {
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        serviceHandler.addOrg(org);
        Optional<Organization> opOrg = serviceHandler.getOrg("testOrg");
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
    public void double_saveOrg(){
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        serviceHandler.addOrg(org);

        assertThrows(IllegalArgumentException.class, ()->{serviceHandler.addOrg(org);});
    }

    @Test
    public void add_currency_to_existing_org(){
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        serviceHandler.addOrg(org);
        serviceHandler.addCurrency("testOrg", "Dollar");
        Optional<Organization> opOrg = serviceHandler.getOrg("testOrg");
        assertTrue(opOrg.get().getOrgCurrencies().size()==2);
        assertTrue(opOrg.get().getOrgCurrencies().contains("Dollar"));
    }

    @Test
    public void update_existing_org(){
        Organization org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO"))
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgSlogan("hello world")
                .build();
        serviceHandler.addOrg(org);
        Organization updated_org = Organization.builder()
                .orgCurrencies(ImmutableSet.of("HELLO_updated"))
                .orgDescription("test_updated")
                .orgName("testOrg_updated")
                .orgOwnerId("owner1_updated")
                .orgPortraitS3Key("ldksjfasdo_updated")
                .orgSlogan("hello world_updated")
                .build();
        serviceHandler.update("testOrg", updated_org);
        Optional<Organization> opOrg = serviceHandler.getOrg("testOrg_updated");
        assertTrue(opOrg.isPresent());
        Organization testOrg = opOrg.get();
        assertEquals(testOrg.getOrgName(), updated_org.getOrgName());
        assertTrue(testOrg.getOrgCurrencies().containsAll(updated_org.getOrgCurrencies()));
        assertEquals(testOrg.getOrgPortraitS3Key(), updated_org.getOrgPortraitS3Key());
        assertEquals(testOrg.getOrgSlogan(), updated_org.getOrgSlogan());
        assertEquals(testOrg.getOrgOwnerId(), updated_org.getOrgOwnerId());
        assertEquals(testOrg.getOrgDescription(), updated_org.getOrgDescription());
    }



    @AfterAll
    public static void teardown() {
        localDBClient.shutdown();
    }

    @AfterEach
    public void cleanup() {
        dbClient.deleteTable(ORG_DDB_TABLE_NAME);
    }
}
