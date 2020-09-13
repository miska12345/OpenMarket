package io.openmarket.organization.service;
import io.openmarket.organization.dao.OrgDaoImpl;
import io.openmarket.organization.model.Organization;

import java.util.Optional;

public class OrgServiceHandler {
    protected OrgDaoImpl orgDao;


    public OrgServiceHandler (OrgDaoImpl orgDao) {
        orgDao = orgDao;
    }

    public void addOrg(Organization org) {
        orgDao.save(org);
    }

    public Optional<Organization> getOrg(String name) {
        return orgDao.load(name);
    }


}
