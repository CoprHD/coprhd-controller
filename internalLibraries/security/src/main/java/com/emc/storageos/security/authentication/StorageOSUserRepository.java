/**
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

import java.util.Map;
import java.net.URI;

import com.emc.storageos.db.client.model.VirtualDataCenter;
import com.emc.storageos.db.common.VdcUtil;
import com.emc.storageos.security.authorization.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.db.client.model.TenantOrg;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.exceptions.SecurityException;

/**
 * Class for creating StorageOSUser instance from user context
 */
public class StorageOSUserRepository {

    private final Logger _logger = LoggerFactory.getLogger(StorageOSUserRepository.class);
    
    private BasePermissionsHelper _permissionsHelper = null;

    @Autowired
    private UserFromRequestHelper _userFromRequestHelper;
    
    private Map<String, StorageOSUser> _localUsers;
    
    public void setLocalUsers(Map<String, StorageOSUser> localUsers) {
        _localUsers = localUsers;
    }

    /**
     * Default constructor
     */
    public StorageOSUserRepository() {      }

    /**
     * Setter for permissions helper object
     * @param helper
     */
    public void setPermissionsHelper(BasePermissionsHelper helper) {
        _permissionsHelper = helper;
    }

    /**
     * From a barebone StorageOSUser add roles if it is a local user.
     * add necessary roles and root tenant id if the local user is root.
     * @param user: StorageOSUser previously constructed
     */
    private void updateLocalUser(StorageOSUser user) {
        StorageOSUser local = _localUsers.get(user.getName());
        if (local != null) {
            for( String role : local.getRoles() ) {

                // if local vdc is in GEO env, local user's security_admin and system_admin role
                // need be downgraded to restricted_security_admin and restricted_system_admin.
                if (!VdcUtil.isLocalVdcSingleSite()) {
                    if (role.equals(Role.SECURITY_ADMIN.toString())) {
                        role = Role.RESTRICTED_SECURITY_ADMIN.toString();
                    }

                    if (role.equals(Role.SYSTEM_ADMIN.toString())) {
                        role = Role.RESTRICTED_SYSTEM_ADMIN.toString();
                    }
                }

                _logger.debug("Adding role {} for user {} from local map", role, user.getName());
                user.addRole(role);
            }
        }
    }
    /**
     * Convenience function to allow passing just a username 
     * (used for security disabler usercase)
     * @param userContext
     * @return StorageOSUser object
     */
    public StorageOSUser findOne(String userContext) {
        StorageOSUser user = _userFromRequestHelper.getStorageOSUser(userContext);
        if(user==null) {
            throw SecurityException.fatals.couldNotConstructUserObjectFromRequest();
        }
        addRoles(user);
        return user;
    }
    
    /**
     * Find StorageOSUser object for the user record looked up from the token
     * @param userDAO: user record
     * @return StorageOSUser instance
     */
    public StorageOSUser findOne(StorageOSUserDAO userDAO) {
        if( userDAO == null) {
            throw SecurityException.fatals
                    .theParametersAreNotValid(StorageOSUserDAO.class.getName());
        }
        StorageOSUser user = new StorageOSUser(userDAO);
        addRoles(user);
        return user;
    }

    /**
     * 
     * For a given StorageOSUser object, add the appropriate tenant ID
     * and zone roles
     *
     * @param user
     */
    private void addRoles(StorageOSUser user) {
        TenantOrg rootTenant = _permissionsHelper.getRootTenant();
        if (user.isLocal()) {
            updateLocalUser(user);
        } else if( rootTenant.getId().equals(URI.create(user.getTenantId())) ) {
            // grab all zone roles for this user
            _permissionsHelper.populateZoneRoles(user, VdcUtil.getLocalVdc());
        }
    }
    
    /**
     * Checks if a local user exists (in the repository map)
     * @param userName
     * @return true if exists, false otherwise.
     */
    public boolean isUserLocal(String userName) {
        return _localUsers.containsKey(userName);
    }
    
}
