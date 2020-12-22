package io.openmarket.organization;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDao;
import io.openmarket.organization.grpc.OrganizationOuterClass;
import io.openmarket.organization.model.Organization;
import lombok.extern.log4j.Log4j2;

import javax.inject.Inject;
import java.util.*;

@Log4j2
public class OrgServiceHandler {
    protected OrgDao orgDao;

    @Inject
    public OrgServiceHandler (OrgDao orgDao) {
        this.orgDao = orgDao;
    }

    public OrganizationOuterClass.orgName addOrgRquest(OrganizationOuterClass.orgMetadata params) {
        Organization org = this.orgMetadata2Org(params);
        this.addOrg(org);
        return OrganizationOuterClass.orgName.newBuilder().setOrgName(params.getOrgName()).build();
    }

    public void addOrg(Organization org) {
        if (org.getOrgName() == null || orgDao.load(org.getOrgName()).isPresent()) {
            log.error("Organization name has been occupied!");
            throw new IllegalArgumentException("Organization name has been occupied!");
        }

        if (org.getOrgDescription() == null) org.setOrgDescription("");
        if (org.getOrgPortraitS3Key() == null) org.setOrgPortraitS3Key("");
        orgDao.save(org);
    }

    public Optional<Organization> getOrg(String name) {
        return orgDao.load(name);
    }

    public OrganizationOuterClass.orgMetadata getOrgRequest(OrganizationOuterClass.orgName params) {
        if (params.getOrgName().isEmpty()) {
            log.error("Missing organization name!");
            throw new IllegalArgumentException("Missing organization name!");
        }
        Organization org = this.getOrg(params.getOrgName()).get();
        if (org == null) {
            log.error("Organization is missing!");
            throw new IllegalArgumentException("Organization is missing!");
        }
        return this.org2OrgMetadata(org);
    }

    public OrganizationOuterClass.orgName partialUpdateRequest(OrganizationOuterClass.orgMetadata params) {
        return null;

    }

    private Organization orgMetadata2Org (OrganizationOuterClass.orgMetadata params) {
        if (!params.getOrgName().isEmpty());
        else {
            log.error("Organization name is missing!");
            throw new IllegalArgumentException("Organization name is missing!");
        };
        Organization org = Organization.builder().orgName(params.getOrgName())
                .orgOwnerId(params.getOrgOwnerId())
                .orgCurrency(params.getOrgCurrency())
                .build();
        if (!params.getOrgDescription().isEmpty()) org.setOrgDescription(params.getOrgDescription());
        else org.setOrgDescription("");

        if (!params.getOrgOwnerId().isEmpty()) org.setOrgOwnerId(params.getOrgOwnerId());
        else {
            log.error("Organization owner name is missing!");
            throw new IllegalArgumentException("Organization owner name is missing!");
        };

        if (!params.getOrgPortraitS3Key().isEmpty()) org.setOrgPortraitS3Key(params.getOrgPortraitS3Key());
        else org.setOrgPortraitS3Key("");

        return org;
    }


    private OrganizationOuterClass.orgMetadata org2OrgMetadata (Organization org) {
        if (org.getOrgName() == null || org.getOrgName().isEmpty()) {
            log.error("Organization name is missing!");
            throw new IllegalArgumentException("Organization name is missing!");
        }

        if (org.getOrgOwnerId() == null || org.getOrgOwnerId().isEmpty()) {
            log.error("Organization owner name is missing!");
            throw new IllegalArgumentException("Organization owner name is missing!");
        }
        return OrganizationOuterClass.orgMetadata.newBuilder()
                .setOrgCurrency(org.getOrgCurrency())
                .setOrgOwnerId(org.getOrgOwnerId())
                .setOrgDescription(org.getOrgDescription())
                .setOrgPortraitS3Key(org.getOrgPortraitS3Key())
                .setOrgName(org.getOrgName())
                .build();
    }


}
