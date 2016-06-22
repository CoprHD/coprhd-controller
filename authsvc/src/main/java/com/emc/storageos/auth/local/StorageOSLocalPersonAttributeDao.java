/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.local;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.auth.impl.LdapFailureHandler;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 * Attribute repository class for local users.
 */
public class StorageOSLocalPersonAttributeDao implements StorageOSPersonAttributeDao {

    @Autowired
    private BasePermissionsHelper _permissionsHelper;

    @Override
    public boolean isGroupValid(final String groupId,
            ValidationFailureReason[] failureReason) {
        return false;
    }

    @Override
    public void validateUser(final String userId, final String tenantId, final String altTenantId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public StorageOSUserDAO getStorageOSUser(final Credentials credentials,
            ValidationFailureReason[] failureReason) {
        String uid = ((UsernamePasswordCredentials) credentials).getUserName();
        if (uid == null) {
            throw APIException.badRequests.theParametersAreNotValid(Credentials.class
                    .getName());
        }
        String rootTenantId = _permissionsHelper.getRootTenant().getId().toString();
        StorageOSUserDAO storageOSUser = new StorageOSUserDAO();
        storageOSUser.setUserName(uid);
        storageOSUser.setTenantId(rootTenantId);
        storageOSUser.setIsLocal(true);
        return storageOSUser;
    }

    @Override
    public StorageOSUserDAO getStorageOSUser(Credentials credentials) {
        // failureReason is not used in local DAO
        return getStorageOSUser(credentials, null);
    }

    /*
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#getUserTenants(java.lang.String)
     */
    @Override
    public Map<URI, UserMapping> getUserTenants(String username) {
        return Collections.singletonMap(_permissionsHelper.getRootTenant().getId(), null);
    }

    @Override
    public Map<URI, UserMapping> peekUserTenants(String username, URI tenantURI, List<UserMapping> userMappings) {
        return getUserTenants(username);
    }

    @Override
    public void setFailureHandler(LdapFailureHandler failureHandler) {

    }
}
