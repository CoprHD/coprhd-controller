/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.auth.local;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.security.authorization.BasePermissionsHelper;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.svcs.errorhandling.resources.APIException;

/**
 *  Attribute repository class for local users. 
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
    public boolean isUserValid(final String userId, final String tenantId,
            final String altTenantId, ValidationFailureReason[] failureReason) {
        return false;
    }

    @Override
    public StorageOSUserDAO getStorageOSUser(final Credentials credentials,
            ValidationFailureReason[] failureReason) {
        String uid = ((UsernamePasswordCredentials)credentials).getUserName();
        if( uid == null ) {
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

    /*
     * @see com.emc.storageos.auth.StorageOSPersonAttributeDao#getUserTenants(java.lang.String)
     */
    @Override
    public Map<URI, UserMapping> getUserTenants(String username) {
        return Collections.singletonMap( _permissionsHelper.getRootTenant().getId(), null);
    }
}
