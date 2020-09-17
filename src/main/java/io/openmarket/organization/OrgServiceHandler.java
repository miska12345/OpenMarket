package io.openmarket.organization;


import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDao;
import io.openmarket.organization.model.Organization;
import lombok.extern.log4j.Log4j2;
import organization.OrganizationOuterClass;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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

        if (org.getOrgOwnerId() == null) {
            log.error("Organization owner name is missing!");
            throw new IllegalArgumentException("Organization owner name is missing!");
        }

        if (org.getOrgOwnerId() == null) org.setOrgDescription("");
        if (org.getOrgCurrencies() == null) org.setOrgCurrencies(ImmutableSet.of());
        if (org.getOrgDescription() == null) org.setOrgDescription("");
        if (org.getOrgPortraitS3Key() == null) org.setOrgPortraitS3Key("");
        if (org.getOrgSlogan() == null) org.setOrgSlogan("");
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

    public void addCurrency(String name, String currency) {
        if (!orgDao.load(name).isPresent()) {
            log.error("Organization name has been occupied!");
            throw new IllegalArgumentException("Organization name has been occupied!");
        }
        Optional<Organization> opOrg = orgDao.load(name);
        Organization org = opOrg.get();
        Set<String> currencies = org.getOrgCurrencies();
        currencies.add(currency);
        orgDao.save(org);
    }

    public OrganizationOuterClass.orgName partialUpdateRequest(OrganizationOuterClass.orgMetadata params) {
        if (params.getOrgName().isEmpty()) {
            log.error("Missing organization name!");
            throw new IllegalArgumentException("Missing organization name!");
        }
        if (!orgDao.load(params.getOrgName()).isPresent()) {
            log.error("Non-existing organization name.");
            throw new IllegalArgumentException("Non-existing organization name.");
        }
        Organization updated_org = (Organization) orgDao.load(params.getOrgName()).get();
        if (!params.getOrgDescription().isEmpty()) updated_org.setOrgDescription(params.getOrgDescription());
        if (!params.getOrgSlogan().isEmpty()) updated_org.setOrgSlogan(params.getOrgSlogan());
        if (!params.getOrgOwnerId().isEmpty()) updated_org.setOrgOwnerId(params.getOrgOwnerId());
        if (!params.getOrgPortraitS3Key().isEmpty()) updated_org.setOrgPortraitS3Key(params.getOrgPortraitS3Key());

        orgDao.save(updated_org);
        return OrganizationOuterClass.orgName.newBuilder().setOrgName(params.getOrgName()).build();
    }

    private Organization orgMetadata2Org (OrganizationOuterClass.orgMetadata params) {
        if (!params.getOrgName().isEmpty());
        else {
            log.error("Organization name is missing!");
            throw new IllegalArgumentException("Organization name is missing!");
        };

        Organization org = Organization.builder().orgName(params.getOrgName()).build();
        if (!params.getOrgCurrenciesList().isEmpty()) org.setOrgCurrencies(new HashSet<>(params.getOrgCurrenciesList()));
        else org.setOrgCurrencies(ImmutableSet.of());

        if (!params.getOrgDescription().isEmpty()) org.setOrgDescription(params.getOrgDescription());
        else org.setOrgDescription("");

        if (!params.getOrgSlogan().isEmpty()) org.setOrgSlogan(params.getOrgSlogan());
        else org.setOrgSlogan("");

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
                .addAllOrgCurrencies(org.getOrgCurrencies())
                .setOrgOwnerId(org.getOrgOwnerId())
                .setOrgSlogan(org.getOrgSlogan())
                .setOrgDescription(org.getOrgDescription())
                .setOrgPortraitS3Key(org.getOrgPortraitS3Key())
                .setOrgName(org.getOrgName())
                .build();
    }


}
