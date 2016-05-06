/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.db.client.upgrade.callbacks;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.impl.DbClientImpl;
import com.emc.storageos.db.client.model.AbstractChangeTrackingSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.client.upgrade.BaseCustomMigrationCallback;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.svcs.errorhandling.resources.MigrationCallbackException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

/**
 * This callback is for 1.1 to 2.0. Move all VDC role assignments from root tenant to new CF VirtualDataCenter.
 */
public class VDCRoleMigrationCallback extends BaseCustomMigrationCallback {

    private static final Logger _log = LoggerFactory.getLogger(VDCRoleMigrationCallback.class);

    public enum Role {
        // VDC roles
        SECURITY_ADMIN,
        SYSTEM_ADMIN,
        SYSTEM_MONITOR,
        SYSTEM_AUDITOR,
        PROXY_USER,
        // Tenant Roles
        TENANT_ADMIN,
        PROJECT_ADMIN,
        TENANT_APPROVER,

        // Internal VDC roles
        RESTRICTED_SECURITY_ADMIN,
        RESTRICTED_SYSTEM_ADMIN
    }

    @Override
    public void process() throws MigrationCallbackException {

        _log.info("VDC Role Migration Started ...");

        DbClient dbClient = getDbClient();

        TenantOrg rootTenant = findRootTenant(dbClient);

        StringSetMap tenantRoles = rootTenant.getRoleAssignments();

        if (tenantRoles == null) {
            _log.info("No Role Assignments in original Root Tenant. Skip moving.");
            return;
        }

        VirtualDataCenter vdc = VdcUtil.getLocalVdc();
        if (vdc == null) {
            throw new IllegalStateException("the CF of Local VDC is not found.");
        }

        // only copy VDC role assignments
        copyRoleAssignments(tenantRoles, vdc);
        removeRoleFromRootTenant(vdc, rootTenant);

        dbClient.persistObject(vdc);
        dbClient.persistObject(rootTenant);

        _log.info("VDC Role Migration Done.");
    }

    private TenantOrg findRootTenant(DbClient _dbClient) {

        URIQueryResultList tenants = new URIQueryResultList();

        try {
            _dbClient.queryByConstraint(
                    ContainmentConstraint.Factory.getTenantOrgSubTenantConstraint(URI.create(TenantOrg.NO_PARENT)),
                    tenants);

            if (tenants.iterator().hasNext() == false) {
                throw new IllegalStateException("The CF of Root Tenant is not found");
            }

            URI root = tenants.iterator().next();
            return _dbClient.queryObject(TenantOrg.class, root);

        } catch (DatabaseException ex) {
            throw new IllegalStateException("Error in finding the CF of root tenant", ex);
        }
    }

    /*
     * copy all Non VDC roles from rootTenantOrg to VDC
     * the structure of role assignments is: <key: user id, value: role list>
     */
    private void copyRoleAssignments(StringSetMap tenantRoleAssignments, VirtualDataCenter vdc) {

        for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : tenantRoleAssignments.entrySet()) {

            String uid = roleAssignment.getKey();

            Iterator<String> itr = roleAssignment.getValue().iterator();
            while (itr.hasNext()) {
                String role = itr.next();
                if (isVDCRole(role)) {
                    _log.info("Found VDC Role [ {} ] for the user [ {} ]. Copy to VDC", role, uid);
                    vdc.addRole(uid, role);
                }
            }
        }
    }

    /**
     * remove VDC roles from rootTenantOrg
     * 
     * @param vdc
     * @param rootTenant
     */
    private void removeRoleFromRootTenant(VirtualDataCenter vdc, TenantOrg rootTenant) {

        StringSetMap vdcRoles = vdc.getRoleAssignments();

        for (Map.Entry<String, AbstractChangeTrackingSet<String>> roleAssignment : vdcRoles.entrySet()) {

            String uid = roleAssignment.getKey();

            Iterator<String> itr = roleAssignment.getValue().iterator();
            while (itr.hasNext()) {
                String role = itr.next();
                rootTenant.removeRole(uid, role);
            }
        }
    }

    private boolean isVDCRole(String role) {
        return (role.equalsIgnoreCase(Role.SYSTEM_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SECURITY_ADMIN.toString()) ||
                role.equalsIgnoreCase(Role.SYSTEM_MONITOR.toString()) || role.equalsIgnoreCase(Role.SYSTEM_AUDITOR.toString()));
    }

    // below all are for test only
    private static void logRole(StringSetMap roleAssignment) {

        for (String key : roleAssignment.keySet()) {
            _log.debug("Role key = {}, value = ", key, roleAssignment.get(key));
        }
    }

    public static DbClient buildDbClient() {
        try {
            ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("/dbclient-conf.xml");

            DbClientImpl dbClient = (DbClientImpl) ctx.getBean("dbclient");

            dbClient.start();

            return dbClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // for test only
    public static void main(String[] args) throws Exception {

        VDCRoleMigrationCallback cb = new VDCRoleMigrationCallback();

        cb.setDbClient(buildDbClient());
        cb.process();
    }

}
