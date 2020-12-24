package io.openmarket.organization.service;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.local.embedded.DynamoDBEmbedded;
import com.amazonaws.services.dynamodbv2.local.shared.access.AmazonDynamoDBLocal;
import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.OrgServiceHandler;
import io.openmarket.organization.dao.OrgDaoImpl;
import io.openmarket.organization.grpc.OrganizationOuterClass;
import io.openmarket.organization.grpc.OrganizationOuterClass.*;
import io.openmarket.organization.model.Organization;
import org.checkerframework.dataflow.qual.TerminatesExecution;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static io.openmarket.config.OrgConfig.*;
import static org.junit.jupiter.api.Assertions.*;

public class OrgServiceHandlerTest {
    private static AmazonDynamoDBLocal localDBClient;
    private static AmazonDynamoDB dbClient;
    private DynamoDBMapper dbMapper;
    private OrgDaoImpl orgDao;
    private OrgServiceHandler serviceHandler;

    public final Organization TEST_ORG = Organization.builder()
            .orgCurrency("HELLO")
            .orgDescription("test")
            .orgName("testOrg")
            .orgOwnerId("owner1")
            .orgPosterS3Key("FABAB")
            .orgPortraitS3Key("ldksjfasdo")
            .build();

    OrganizationOuterClass.orgMetadata TEST_REQUEST = OrganizationOuterClass.orgMetadata.newBuilder()
            .setOrgCurrency("HELLO")
            .setOrgDescription("test")
            .setOrgName("testOrg")
            .setOrgOwnerId("owner1")
            .setOrgPortraitS3Key("ldksjfasdo")
            .setOrgPosterS3Key("FABAB")
            .build();

    private static void createTable() {
        dbClient.createTable(new CreateTableRequest().withTableName(ORG_DDB_TABLE_NAME)
                .withKeySchema(ImmutableList.of(new KeySchemaElement(ORG_DDB_KEY_ORGNAME, KeyType.HASH)))
                .withAttributeDefinitions(new AttributeDefinition(ORG_DDB_KEY_ORGNAME, ScalarAttributeType.S))
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
        serviceHandler.addOrg(this.TEST_ORG);
        ScanResult result = dbClient.scan(new ScanRequest().withTableName(ORG_DDB_TABLE_NAME));
        assertEquals(1, result.getItems().size());
    }

    @Test
    public void saveOrgRequest_then_getOrg_by_name() {
        serviceHandler.addOrgRquest(this.TEST_REQUEST);

        ScanResult result = dbClient.scan(new ScanRequest().withTableName(ORG_DDB_TABLE_NAME));
        assertEquals(1, result.getItems().size());
        Organization org = serviceHandler.getOrg("testOrg").get();
        assertEquals(TEST_REQUEST.getOrgName(), org.getOrgName());
        assertTrue(TEST_REQUEST.getOrgCurrency().equals(org.getOrgCurrency()));
        assertEquals(TEST_REQUEST.getOrgPortraitS3Key(), org.getOrgPortraitS3Key());
        assertEquals(TEST_REQUEST.getOrgOwnerId(), org.getOrgOwnerId());
        assertEquals(TEST_REQUEST.getOrgDescription(), org.getOrgDescription());
    }

    @Test
    public void saveOrg_then_getOrg_by_name() {
        Organization org = Organization.builder()
                .orgCurrency("HELLO")
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPosterS3Key("FABAB")
                .orgPortraitS3Key("ldksjfasdo")
                .build();
        serviceHandler.addOrg(org);
        Optional<Organization> opOrg = serviceHandler.getOrg("testOrg");
        assertTrue(opOrg.isPresent());
        Organization testOrg = opOrg.get();
        assertEquals(testOrg.getOrgName(), org.getOrgName());
        assertTrue(testOrg.getOrgCurrency().equals(org.getOrgCurrency()));
        assertEquals(testOrg.getOrgPortraitS3Key(), org.getOrgPortraitS3Key());
        assertEquals(testOrg.getOrgOwnerId(), org.getOrgOwnerId());
        assertEquals(testOrg.getOrgDescription(), org.getOrgDescription());
    }

    @Test
    public void isUserFollowing_when_user_is() {
        serviceHandler.addOrgRquest(TEST_REQUEST);
        serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard").build());
        assertEquals(true, serviceHandler.isUserFollowing(IsUserFollowingRequest
                .newBuilder()
                .setOrgId(TEST_REQUEST.getOrgName())
                .setUserId("lookchard")
                .build()
            ).getIsFollowing()
        );
    }

    @Test
    public void isUserFollowing_when_user_is_not() {
        serviceHandler.addOrgRquest(TEST_REQUEST);
        assertEquals(false, serviceHandler.isUserFollowing(IsUserFollowingRequest
                        .newBuilder()
                        .setOrgId(TEST_REQUEST.getOrgName())
                        .setUserId("lookchard")
                        .build()
                ).getIsFollowing()
        );
    }

    @Test
    public void isUserFollowing_when_org_doesnt_exist() {
        assertEquals(false, serviceHandler.isUserFollowing(IsUserFollowingRequest
                        .newBuilder()
                        .setOrgId(TEST_REQUEST.getOrgName())
                        .setUserId("lookchard")
                        .build()
                ).getIsFollowing()
        );
    }

    @Test
    public void saveOrg_then_getOrgRequest_by_name() {
        Organization org = Organization.builder()
                .orgCurrency("HELLO")
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgPosterS3Key("FABAB")
                .build();
        serviceHandler.addOrg(org);
        OrganizationOuterClass.orgName req = OrganizationOuterClass.orgName.newBuilder()
                .setOrgName(org.getOrgName())
                .build();
        OrganizationOuterClass.orgMetadata ret = serviceHandler.getOrgRequest(req);

        assertEquals(ret.getOrgName(), org.getOrgName());
        assertTrue(ret.getOrgCurrency().equals(org.getOrgCurrency()));
        assertEquals(ret.getOrgPortraitS3Key(), org.getOrgPortraitS3Key());
        assertEquals(ret.getOrgOwnerId(), org.getOrgOwnerId());
        assertEquals(ret.getOrgDescription(), org.getOrgDescription());
    }

    @Test
    public void double_saveOrg(){
        Organization org = Organization.builder()
                .orgCurrency("HELLO")
                .orgDescription("test")
                .orgName("testOrg")
                .orgOwnerId("owner1")
                .orgPortraitS3Key("ldksjfasdo")
                .orgPosterS3Key("FABAB")
                .build();
        serviceHandler.addOrg(org);

        assertThrows(IllegalArgumentException.class, ()->{serviceHandler.addOrg(org);});
    }

    @Test
    public void can_update_followers_if_new() {
        this.serviceHandler.addOrgRquest(TEST_REQUEST);

        this.serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard").build());

        Organization updated = this.serviceHandler.getOrg(TEST_REQUEST.getOrgName()).get();

        assertEquals(1, updated.getOrgFollowerCount());
        assertEquals("lookchard", this.serviceHandler.getFollowerIds(TEST_REQUEST.getOrgName()).get(0));

    }

    @Test
    public void can_update_followers_if_not_contains() {
        this.serviceHandler.addOrgRquest(TEST_REQUEST);

        this.serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard").build());
        this.serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard2").build());

        Organization updated = this.serviceHandler.getOrg(TEST_REQUEST.getOrgName()).get();

        assertEquals(2, updated.getOrgFollowerCount());

    }

    @Test
    public void cannot_update_followers_if_contains() {
        this.serviceHandler.addOrgRquest(TEST_REQUEST);

        this.serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard").build());
        this.serviceHandler.updateFollower(UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard").build());

        Organization updated = this.serviceHandler.getOrg(TEST_REQUEST.getOrgName()).get();
        assertEquals(1, updated.getOrgFollowerCount());

    }

    @Test
    public void update_follower_insanity_check() throws InterruptedException {
        this.serviceHandler.addOrgRquest(TEST_REQUEST);
        List<Callable<String>> tasks = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 10; i++) {
            int finalI = i;
            Callable<String> task = () -> {
                UpdateFollowerRequest request = UpdateFollowerRequest.newBuilder().setOrgId(TEST_REQUEST.getOrgName()).setUserId("lookchard" + finalI).build();
                this.serviceHandler.updateFollower(request);
                return "";
            };
            tasks.add(task);
        }

        executor.invokeAll(tasks);

        Organization updated = this.serviceHandler.getOrg(TEST_REQUEST.getOrgName()).get();
        assertEquals(10, updated.getOrgFollowerCount());
    }



    @Test
    public void partial_update_org(){
        serviceHandler.addOrg(TEST_ORG);

        OrganizationOuterClass.orgMetadata request = OrganizationOuterClass.orgMetadata.newBuilder()
                .setOrgDescription("Description")
                .setOrgName("testOrg")
                .setOrgPortraitS3Key("key")
                .setOrgPosterS3Key("FABA")
                .setOrgOwnerId("owner1").build();
        serviceHandler.partialUpdateRequest(request);

        Optional<Organization> opOrg = serviceHandler.getOrg("testOrg");
        assertTrue(opOrg.isPresent());
        Organization testOrg = opOrg.get();
        assertEquals(testOrg.getOrgName(), "testOrg");
        assertTrue(testOrg.getOrgCurrency().equals(TEST_ORG.getOrgCurrency()));
        assertEquals(testOrg.getOrgPortraitS3Key(), "key");
        assertEquals(testOrg.getOrgPosterS3Key(), "FABA");
        assertEquals(testOrg.getOrgOwnerId(), TEST_ORG.getOrgOwnerId());
        assertEquals(testOrg.getOrgDescription(), "Description");


    }

    @Test
    public void cannotUpdate_without_orgName(){
        serviceHandler.addOrg(TEST_ORG);

        OrganizationOuterClass.orgMetadata request = OrganizationOuterClass.orgMetadata.newBuilder()
                .setOrgDescription("Description")
                .setOrgPortraitS3Key("key")
                .setOrgPosterS3Key("FABA")
                .setOrgOwnerId("owner1").build();

        assertThrows(IllegalArgumentException.class, ()->this.serviceHandler.partialUpdateRequest(request));
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
