/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.auth;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.Credentials;

import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;
import com.emc.storageos.security.resource.UserInfoPage.UserDetails;
import com.emc.storageos.svcs.errorhandling.resources.BadRequestException;

/**
 * Authentication manager interface - provides api to validate user credentials
 * and to resolve a StorageOSUser from the authenticated credentials
 */
public interface AuthenticationManager {

    public enum ValidationFailureReason {
        LDAP_CONNECTION_FAILED,
        LDAP_MANAGER_AUTH_FAILED,
        USER_OR_GROUP_NOT_FOUND_FOR_TENANT,
        LDAP_CANNOT_SEARCH_GROUP_IN_LDAP_MODE,
    }

    /**
     * Validates credentials provided
     * 
     * @param credentials
     * @return StorageOSUserDAO if user is successfully authenticated, null otherwise
     */
    public StorageOSUserDAO authenticate(final Credentials credentials);

    /**
     * validate the user against the tenant provided
     * 
     * @param userId
     * @param tenantId
     */
    public void validateUser(final String userId, final String tenantId, final String altTenantId);

    /**
     * validate the group name
     * 
     * @param groupId
     * @param failureReason put parameter which explains why the validation failed
     * @return true if it exists in at least one of the domains configured, false otherwise
     */
    public boolean isGroupValid(final String groupId,
            ValidationFailureReason[] failureReason);

    /**
     * Reload the list of authentication providers from the database
     */
    public void reload();

    /**
     * Initialize the authentication manager
     */
    public void init();

    /**
     * shutdown
     */
    public void shutdown();

    /**
     * Get a map of tenancies a user maps to and the applied user mapping
     * 
     * @param username name of the user
     * @return A map with tenant ID as the key and the applied mapping as the value
     */
    public Map<URI, UserMapping> getUserTenants(String username);

    public Map<URI, UserMapping> peekUserTenants(String username, URI tenantUri, List<UserMapping> userMappings);

    /**
     * Gets the user's details- tenant and groups.
     * 
     * @param username name of the user
     * @return A collection of the names of the groups
     */
    public UserDetails getUserDetails(final String username);

    /**
     * Refreshes the specified user in the DB
     * 
     * @param username
     *            name of the user
     */
    public void refreshUser(String username) throws SecurityException,
            BadRequestException;
}
