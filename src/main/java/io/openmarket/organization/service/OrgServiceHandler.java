package io.openmarket.organization.service;
import com.google.common.collect.ImmutableSet;
import io.openmarket.organization.dao.OrgDaoImpl;
import io.openmarket.organization.model.Organization;
import lombok.extern.log4j.Log4j2;
import organization.OrganizationOuterClass;

import java.util.Optional;
import java.util.Set;

@Log4j2
public class OrgServiceHandler {
    protected OrgDaoImpl orgDao;


    public OrgServiceHandler (OrgDaoImpl orgDao) {
        this.orgDao = orgDao;
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

    public void update(String name, Organization org) {
        if (!orgDao.load(name).isPresent()) {
            log.error("Organization name has been occupied!");
            throw new IllegalArgumentException("Organization name has been occupied!");
        }
        orgDao.save(org);
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

    public void partial_update(OrganizationOuterClass.orgMetadata params) {
        if (params == null) {
            log.error("Invalid request!");
            throw new IllegalArgumentException("Invalid request!");
        }
        if (params.getOrgName() == null) {
            log.error("Missing organization name!");
            throw new IllegalArgumentException("Missing organization name!");
        }
        if (!orgDao.load(params.getOrgName()).isPresent()) {
            log.error("Non-existing organization name.");
            throw new IllegalArgumentException("Non-existing organization name.");
        }
        Organization updated_org = new Organization();
        if (params.getOrgDescription() != null) updated_org.setOrgDescription(params.getOrgDescription());
        if (params.getOrgSlogan() != null) updated_org.setOrgSlogan(params.getOrgSlogan());
        if (params.getOrgOwnerId() != null) updated_org.setOrgOwnerId(params.getOrgOwnerId());
        if (params.getOrgPortraitS3Key() != null) updated_org.setOrgPortraitS3Key(params.getOrgPortraitS3Key());
        this.update(params.getOrgName(), updated_org);
    }

}
