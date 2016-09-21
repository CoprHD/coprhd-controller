/*
 *
 *  * Copyright (c) 2016 EMC Corporation
 *  * All Rights Reserved
 *
 */

package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.AuthenticationManager;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class TenantMapper {
    private static Logger log = LoggerFactory.getLogger(TenantMapper.class);

    /**
     * Match the user to one and only one tenant if found user there attributes/groups
     *
     * @param domains
     * @param storageOSUser
     * @param attributeKeyValuesMap
     * @param tenantToMappingMap
     */
    public static Map<URI, BasePermissionsHelper.UserMapping> mapUserToTenant(
            StringSet domains, StorageOSUserDAO storageOSUser,
            Map<String, List<String>> attributeKeyValuesMap, Map<URI,
            List<BasePermissionsHelper.UserMapping>> tenantToMappingMap,
            DbClient dbClient) {

        Map<URI, BasePermissionsHelper.UserMapping> tenants = new HashMap<URI, BasePermissionsHelper.UserMapping>();
        if (CollectionUtils.isEmpty(domains)) {
            return tenants;
        }

        List<BasePermissionsHelper.UserMappingAttribute> userMappingAttributes = new ArrayList<BasePermissionsHelper.UserMappingAttribute>();

        for (Map.Entry<String, List<String>> attributeKeyValues : attributeKeyValuesMap.entrySet()) {
            BasePermissionsHelper.UserMappingAttribute userMappingAttribute = new BasePermissionsHelper.UserMappingAttribute();
            userMappingAttribute.setKey(attributeKeyValues.getKey());
            userMappingAttribute.setValues(attributeKeyValues.getValue());
            userMappingAttributes.add(userMappingAttribute);
        }

        List<String> userMappingGroups = new ArrayList<String>();
        if (null != storageOSUser.getGroups()) {
            for (String group : storageOSUser.getGroups()) {
                userMappingGroups.add((group.split("@")[0]).toUpperCase());
                log.debug("Adding user's group {} to usermapping group ", (group.split("@")[0]).toUpperCase());
            }
        }

        for (Map.Entry<URI, List<BasePermissionsHelper.UserMapping>> tenantToMappingMapEntry : tenantToMappingMap.entrySet()) {
            if (tenantToMappingMapEntry == null || tenantToMappingMapEntry.getValue() == null) {
                continue;
            }

            for (String domain : domains) {
                for (BasePermissionsHelper.UserMapping userMapping : tenantToMappingMapEntry.getValue()) {
                    if (userMapping.isMatch(domain, userMappingAttributes, userMappingGroups)) {
                        tenants.put(tenantToMappingMapEntry.getKey(), userMapping);
                    }
                }
            }
        }

        // if no tenant was found then set it to the root tenant
        // unless the root tenant is restricted by a mapping
        if (tenants.isEmpty()) {
            BasePermissionsHelper permissionsHelper = new BasePermissionsHelper(dbClient, false);
            TenantOrg rootTenant = permissionsHelper.getRootTenant();

            // check if UserMappingMap parameter contains provider tenant or not.
            // if yes, means Provider Tenant's user-mapping under modification.
            if (tenantToMappingMap.containsKey(rootTenant.getId())) {
                List<BasePermissionsHelper.UserMapping> rootUserMapping = tenantToMappingMap.get(rootTenant.getId());

                // check if the change is to remove all user-mapping from provider tenant.
                // if yes, set user map to provider tenant.
                if (CollectionUtils.isEmpty(rootUserMapping)) {
                    log.debug("User {} did not match a tenant.  Assigning to root tenant since root does not have any attribute mappings",
                            storageOSUser.getUserName());
                    tenants.put(rootTenant.getId(), null);
                }

                // provider tenant is not in UserMapping parameter, means no change to its user-mapping in this request,
                // need to check if its original user-mapping is empty or not.
            } else if (rootTenant.getUserMappings() == null || rootTenant.getUserMappings().isEmpty()) {
                log.debug("User {} did not match a tenant.  Assigning to root tenant since root does not have any attribute mappings",
                        storageOSUser.getUserName());
                tenants.put(rootTenant.getId(), null);
            }
        }
        return tenants;
    }

}
