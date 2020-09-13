package io.openmarket.organization.dao;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.google.common.collect.ImmutableMap;
import io.openmarket.dao.dynamodb.AbstractDynamoDBDao;
import io.openmarket.organization.model.Organization;

import javax.inject.Inject;
import java.util.Map;
import java.util.Optional;

public class OrgDaoImpl extends AbstractDynamoDBDao<Organization> implements OrgDao {

    @Inject
    public OrgDaoImpl(AmazonDynamoDB dbClient, DynamoDBMapper dbMapper) {
        super(dbClient, dbMapper);
    }

    @Override
    public Optional getOrganization(String orgName, String projection) {
        return Optional.empty();
    }

    @Override
    public Optional load(String key) {
        return super.load(Organization.class, key);
    }

    @Override
    protected boolean validate(Organization obj) {
        if (obj.getOrgCurrencies() == null
                || obj.getOrgCurrencies().isEmpty()
                || obj.getOrgDescription() == null
                || obj.getOrgOwnerId() == null
                || obj.getOrgPortraitS3Key() == null
                || obj.getOrgSlogan() == null
                || obj.getOrgName() == null
        ) return false;


        return true;
    }



}
