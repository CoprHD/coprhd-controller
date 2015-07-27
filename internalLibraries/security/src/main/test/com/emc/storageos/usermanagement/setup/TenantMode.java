/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.usermanagement.setup;

import com.emc.storageos.model.tenant.UserMappingParam;
import com.emc.storageos.usermanagement.model.RoleOrAcl;
import com.emc.storageos.usermanagement.util.ViPRClientHelper;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;


public class TenantMode extends ADMode {

    private static Logger logger = LoggerFactory.getLogger(TenantMode.class);
    private static List<UserMappingParam> oldRootTenantUserMappingList;

    private static Map<URI,Map<RoleOrAcl, String>> roleUserMap = new HashMap<URI, Map<RoleOrAcl, String>>();

    protected static URI rootTenantID;
    protected static ViPRClientHelper viPRClientHelper;

    @BeforeClass
    public synchronized static void setup_TenantModeBaseClass() throws Exception {
        rootTenantID = superUserClient.getUserTenantId();
        viPRClientHelper = new ViPRClientHelper(superUserClient);

        // keep root tenant's user mapping, will restore it in teardown method
        viPRClientHelper.addRoleAssignment(rootTenantID, superUser, RoleOrAcl.TenantAdmin.toString());
        oldRootTenantUserMappingList = viPRClientHelper.removeTenantUserMapping(rootTenantID);
    }

    @AfterClass
    public static void teardown_TenantModeBaseClass() throws Exception {

        // add old user mappings back
        logger.info("restore root tenant user mappings");
        viPRClientHelper.removeTenantUserMapping(rootTenantID);
        viPRClientHelper.addUserMappingToTenant(rootTenantID, oldRootTenantUserMappingList);

        // delete all users from LDAP/AD server
        logger.info("remove users from ldap server");
        Iterator it = roleUserMap.keySet().iterator();
        while(it.hasNext()) {
            URI id = (URI)it.next();

            Map map = (Map) roleUserMap.get(id);
            Iterator subIt = map.keySet().iterator();

            String role = (String)subIt.next();
            String user = (String) map.get(role);


            if (!role.equalsIgnoreCase("norole")) {
                logger.info("remove " + role + " from " + user + " on ID: " + id);
                if (id.toString().equals("vdc")) {
                    id = null;
                }
                viPRClientHelper.removeRoleAssignment(id, user, role);
            }

            logger.info("remove user: " + user);
            adClient.deleteUser(user);
        }
    }


    public static String getUserByRole(URI tenantOrProjectURI, RoleOrAcl roleOrAcl) throws Exception {

        URI tempURI = tenantOrProjectURI;
        if (tempURI == null) {
            tempURI = new URI("vdc");
        }

        Map map = roleUserMap.get(tempURI);
        if (map == null) {
            map = new HashMap<RoleOrAcl, String>();
            roleUserMap.put(tempURI, map);
        }

        String key = null;
        if (roleOrAcl != null) {
            key = roleOrAcl.getRoleName();
        } else {
            key = "norole";
        }

        String user = (String)map.get(key);


        if (user !=null ) {
            return user + "@" + adClient.getDomainName();
        } else {
            if (roleOrAcl == null) {
                user = "norole_" + new Random().nextInt(10000);
            } else {
                user = roleOrAcl.getRoleName()+ "_" + new Random().nextInt(10000);
            }
            adClient.createUser(user, PASSWORD, null, null);
            String accountName = user + "@" + adClient.getDomainName();

            if (roleOrAcl != null) {
                viPRClientHelper.addRoleAssignment(tenantOrProjectURI, accountName, roleOrAcl.getRoleName());
            }

            map.put(key, accountName);
            return accountName;
        }
    }
}
