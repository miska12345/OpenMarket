package io.openmarket.organization.dao;

import io.openmarket.dao.dynamodb.DynamoDBDao;
import io.openmarket.organization.model.Organization;

import java.util.Optional;

public interface OrgDao extends DynamoDBDao<Organization> {
    public Optional<Organization> getOrganization(String orgName, String projection);
}
