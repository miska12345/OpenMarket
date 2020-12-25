package io.openmarket.organization;


import com.amazonaws.services.dynamodbv2.model.*;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDao;
import io.openmarket.organization.grpc.OrganizationOuterClass.*;
import io.openmarket.organization.model.Organization;
import lombok.extern.log4j.Log4j2;
import org.checkerframework.checker.nullness.Opt;

import javax.inject.Inject;
import java.util.*;

import static io.openmarket.config.OrgConfig.*;

@Log4j2
public class OrgServiceHandler {
    protected OrgDao orgDao;


    @Inject
    public OrgServiceHandler (OrgDao orgDao) {
        this.orgDao = orgDao;
    }

    public orgName addOrgRquest(orgMetadata params) {
        validateOrgMetaDataOnCreate(params);

        Optional<Organization> exist = orgDao.load(params.getOrgName());
        if (exist.isPresent()) {
            log.info("Organization name has been occupied!");
            return orgName.newBuilder().setOrgName(params.getOrgName()).build();
        }

        Organization newOrg = orgMetadata2Org(params);

        this.orgDao.save(newOrg);

        //TODO change this to addOrgResult later
        return orgName.newBuilder().setOrgName(params.getOrgName()).build();
    }

    public void addOrg(Organization org) {
        if (orgDao.load(org.getOrgName()).isPresent()) {
            log.error("Organization name has been occupied!");
            throw new IllegalArgumentException("Organization name has been occupied!");
        }

        if (org.getOrgDescription() == null) org.setOrgDescription("");
        orgDao.save(org);
    }


    //TODO add log 4 j debug
    public UpdateFollowerResult updateFollower(UpdateFollowerRequest request) {
        if (request.getOrgId() == null || request.getOrgId().isEmpty()) {
            log.error("Tried to update follower without org id");
            throw new IllegalArgumentException("Tried to update follower without org id");
        }

        //TODO if want to speed this up, have some way to update more followers with 1 request.
        final UpdateItemRequest update = new UpdateItemRequest()
                .withTableName(ORG_DDB_TABLE_NAME)
                .withKey(ImmutableMap.of(ORG_DDB_KEY_ORGNAME, new AttributeValue(request.getOrgId())))
                .withUpdateExpression("ADD #follower :newFollower, #followerCount :val")
                .withConditionExpression("NOT(:newFollower IN (#follower))")
                .withExpressionAttributeNames(ImmutableMap.of(
                        "#follower", ORG_DDB_ATTRIBUTE_FOLLOWERS,
                        "#followerCount", ORG_DDB_ATTRIBUTE_FOLLOWERS_COUNT
                ))
                .withExpressionAttributeValues(ImmutableMap.of(
                        ":newFollower", new AttributeValue().withSS(request.getUserId()),
                        ":val", new AttributeValue().withN("1")
                ));

        try {
            this.orgDao.updateOrg(update);
        }catch (ConditionalCheckFailedException e) {
            log.error(e);
        }
        return UpdateFollowerResult.newBuilder().build();
    }

    public GetFollowerResult getFollowerIds(GetFollowerRequest request) {
        List<String> followers = this.orgDao.getFollowerIds(request.getOrgId());
        return GetFollowerResult.newBuilder().addAllUserIds(followers).build();
    }

    public IsUserFollowingResult isUserFollowing(IsUserFollowingRequest request) {
        if (request.getUserId() == null || request.getUserId().isEmpty()){
            log.error("Missing user id in isUserFollowing");
            throw new IllegalArgumentException();
        } else if (request.getOrgId() == null || request.getOrgId().isEmpty()) {
            log.error("Missing org id in isUserFollowing");
            throw new IllegalArgumentException();
        }

        QueryRequest queryRequest = new QueryRequest()
                .withTableName(ORG_DDB_TABLE_NAME)
                .withKeyConditionExpression("#id = :orgId")
                .withFilterExpression("contains(#followers, :userId)")
                .withExpressionAttributeNames(ImmutableMap.of(
                        "#id", ORG_DDB_KEY_ORGNAME,
                        "#followers", ORG_DDB_ATTRIBUTE_FOLLOWERS
                ))
                .withExpressionAttributeValues(ImmutableMap.of(
                        ":orgId", new AttributeValue(request.getOrgId()),
                        ":userId", new AttributeValue(request.getUserId())
                ));

        QueryResult result = this.orgDao.queryOrg(queryRequest);

        IsUserFollowingResult rpcResult = IsUserFollowingResult
                .newBuilder()
                .setIsFollowing(result.getItems().size() > 0)
                .build();

        return rpcResult;
    }

    public Optional<Organization> getOrg(String name) {
        return orgDao.load(name);
    }

    public orgMetadata getOrgRequest(orgName params) {
        if (params.getOrgName() == null || params.getOrgName().isEmpty()) {
            log.error("Missing organization name!");
            throw new IllegalArgumentException("Missing organization name! on get organization request");
        }
        Organization org = this.getOrg(params.getOrgName()).get();
        if (org == null) {
            log.error("Organization is missing!");
            throw new IllegalArgumentException("Organization is missing!");
        }
        return this.org2OrgMetadata(org);
    }

    public OrgUpdateResult partialUpdateRequest(orgMetadata params) {
        if (params.getOrgName() == null || params.getOrgName().isEmpty()){
            log.error("Missing organization name!");
            throw new IllegalArgumentException("Missing organization name on update request!");
        }
        Optional<Organization> result = this.orgDao.load(params.getOrgName());

        if (!result.isPresent()) {
            log.info("Tried to update an organization '{}' and it doesn't exist", params.getOrgName());
            return OrgUpdateResult.newBuilder().setError(OrgUpdateResult.UpdateError.CANNOT_FIND_ORG).build();
        }

        Organization toUpdate = result.get();

        if (!params.getOrgPosterS3Key().isEmpty()) {
            toUpdate.setOrgPosterS3Key(params.getOrgPosterS3Key());
        }

        if (!params.getOrgDescription().isEmpty()) {
            toUpdate.setOrgDescription(params.getOrgDescription());
        }

        if (!params.getOrgPortraitS3Key().isEmpty()) {
            toUpdate.setOrgPortraitS3Key(params.getOrgPortraitS3Key());
        }

        if (!params.getOrgOwnerId().isEmpty()) {
            toUpdate.setOrgOwnerId(params.getOrgOwnerId());
        }

        this.orgDao.save(toUpdate);

        return OrgUpdateResult.newBuilder().setError(OrgUpdateResult.UpdateError.NONE).build();

    }

    private void validateOrgMetaDataOnCreate(orgMetadata params) {
        if (params.getOrgName().isEmpty() || params.getOrgName() == null){
            log.error("Organization name is missing!");
            throw new IllegalArgumentException("Organization name is missing!");
        }

        if (params.getOrgPosterS3Key() == null || params.getOrgPosterS3Key().isEmpty()) {
            log.error("Organization poster s3 key is missing");
            throw new IllegalArgumentException("Organization poster s3 key is missing");
        }

        if (params.getOrgPortraitS3Key() == null || params.getOrgPortraitS3Key().isEmpty()) {
            log.error("Organization portrait s3 key is missing");
            throw new IllegalArgumentException("Organization portrait s3 key is missing");
        }

        if (params.getOrgOwnerId() == null || params.getOrgOwnerId().isEmpty()) {
            log.error("Organization owner id is missing");
            throw new IllegalArgumentException("Organization owner is missing");
        }

        if (params.getOrgCurrency() == null || params.getOrgCurrency().isEmpty()) {
            log.error("Organization currency is missing");
            throw new IllegalArgumentException("Organization currency is missing");
        }
    }

    private Organization orgMetadata2Org (orgMetadata params) {

        return Organization.builder().orgName(params.getOrgName())
                .orgPosterS3Key(params.getOrgPosterS3Key())
                .orgPortraitS3Key(params.getOrgPortraitS3Key())
                .orgDescription(params.getOrgDescription())
                .orgOwnerId(params.getOrgOwnerId())
                .orgCurrency(params.getOrgCurrency())
                .build();

    }


    private orgMetadata org2OrgMetadata (Organization org) {
        if (org.getOrgName() == null || org.getOrgName().isEmpty()) {
            log.error("Organization name is missing!");
            throw new IllegalArgumentException("Organization name is missing!");
        }

        if (org.getOrgPosterS3Key().isEmpty()) {
            log.error("Organization poster key is missing");
            throw new IllegalArgumentException();
        }

        if (org.getOrgPortraitS3Key().isEmpty()) {
            log.error("Organization portrait key is missing");
            throw new IllegalArgumentException();
        }

        if (org.getOrgOwnerId().isEmpty()) {
            log.error("Organization owner name is missing!");
            throw new IllegalArgumentException("Organization owner name is missing!");
        }
        return orgMetadata.newBuilder()
                .setOrgCurrency(org.getOrgCurrency())
                .setOrgOwnerId(org.getOrgOwnerId())
                .setOrgDescription(org.getOrgDescription())
                .setOrgPortraitS3Key(org.getOrgPortraitS3Key())
                .setOrgName(org.getOrgName())
                .build();
    }


}
