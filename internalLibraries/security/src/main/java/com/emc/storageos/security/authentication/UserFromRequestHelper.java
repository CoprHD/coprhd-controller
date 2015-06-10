/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2012-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

/**
 *  This helper class encapsulate the way attributes are extracted/parsed from the Principal object.
 *  At the moment, it supports the old format:  user,user@domain;group,group,  and the AD attribute release.
 *  In the future, the old format will be removed and potentially other LDAP attribute retrieval code will be incorporated.
 */

import java.net.URI;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMappingAttribute;


public class UserFromRequestHelper {

    private final Logger _logger = LoggerFactory.getLogger(getClass());

    private final static String USERDETAILS_GROUPS = "RecursiveGroups";
    private final static String USERDETAILS_AD_CN = "CN";
    public final static String USERDETAILS_LOCALUSER = "LOCAL_STORAGEOS_USER";
    public final static String USERDETAILS_TENANT_ID = "TENANT_ID";
    private BasePermissionsHelper _permissionsHelper = null;

    public UserFromRequestHelper() {
    }

    /**
     * Setter for permissions helper object
     * @param helper
     */
    public void setPermissionsHelper(BasePermissionsHelper helper) {
        _permissionsHelper = helper;
    }

    /**
     * Constructs a bare bone StorageOSUser from a String based user context 
     * (old format)
     * @param userContext
     * @return StorageOSUser
     */
    public StorageOSUser getStorageOSUser(String userContext) {
        return parseOldFormat(userContext);
    }
     
    /**
     * This method parses the userContext information using the "old" format 
     * ( "user,user@domain.com;group,group2")
     * TODO: once AD integration is complete and attribute release is only 
     * available through that channel, this old format should be removed.  For 
     * now, keeping for backwards compatibility and so that authz testing can 
     * continue without AD servers.
     * @param userContext
     * @return a UserFromRequest pojo
     */
    private StorageOSUser parseOldFormat(String userContext) {
        StorageOSUser user = null;

        if (!StringUtils.isBlank(userContext)) {
            String[] userInfo = userContext.split(";");
            String[] userAttributes = userInfo[0].split(",");
            String name = userAttributes[0];
            String[] parts = name.split("@");
            String domain = "";
            if( parts.length > 1 ) {
                domain = parts[1];
            }
            URI tenant = null;
            boolean local = false;
            if( userAttributes.length > 1 && null != userAttributes[1]  && !StringUtils.isBlank(userAttributes[1])) {
                String[] attrKV = userAttributes[1].split("=");
                if (attrKV[0].equals(USERDETAILS_LOCALUSER)) {
                    if( attrKV.length > 1 && Boolean.valueOf(attrKV[1])) {
                        local = true;
                    }
                } else {
                    UserMapping mapping = new UserMapping();
                    mapping.setDomain(domain);
                    if(attrKV.length > 1) {
                        if(attrKV[0].equalsIgnoreCase("group")) {
                            mapping.setGroups(Collections.singletonList(attrKV[1]));
                        } else {
                            UserMappingAttribute tenantAttribute = new UserMappingAttribute();
                            tenantAttribute.setKey(attrKV[0]);
                            tenantAttribute.setValues(Collections.singletonList(attrKV[1]));
                        }
                        try {
                            tenant = _permissionsHelper.lookupTenant(mapping);
                        } catch (DatabaseException e) {
                            _logger.error("Failed to query for tenant with attribute: {}.  Exception {} ", mapping.toString(), e);
                        }
                    }
                }
            } else if( !domain.isEmpty()) {
                UserMapping mapping = new UserMapping();
                mapping.setDomain(domain);
                
                try {
                    tenant = _permissionsHelper.lookupTenant(mapping);
                } catch (DatabaseException e) {
                    _logger.error("Failed to query for tenant with attribute: {}.  Exception {} ", mapping.toString(), e);
                }
            }
        
            if (null == tenant ) {
                tenant = _permissionsHelper.getRootTenant().getId();
            }
            user = new StorageOSUser(name, tenant.toString());
            user.setIsLocal(local);
            if (userInfo.length > 1) {
                String[] groups = org.springframework.util.StringUtils
                        .commaDelimitedListToStringArray(userInfo[1]);
                if (groups.length > 0) {
                    for (String group : groups) {
                        user.addGroup(group);
                    }
                }
            }
            return user;
        }
        return null;
    }   
}
