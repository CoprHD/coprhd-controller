/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth;

import java.net.URI;
import java.util.Map;
import java.util.List;

import com.emc.storageos.auth.impl.LdapFailureHandler;
import org.apache.commons.httpclient.Credentials;

import com.emc.storageos.auth.AuthenticationManager.ValidationFailureReason;
import com.emc.storageos.db.client.model.StorageOSUserDAO;
import com.emc.storageos.security.authorization.BasePermissionsHelper.UserMapping;

/**
 * Base class for user attribute repositories
 */
public interface StorageOSPersonAttributeDao {

    /**
     * Check if a group is valid in the current configuration
     * 
     * @param groupId - group ID to check
     * @return true if the group is valid in this authentication config
     */
    public boolean isGroupValid(final String groupId,
            ValidationFailureReason[] failureReason);

    /**
     * Check if a user is valid within the specified tenant
     * 
     * @param userId ID of the user to check
     * @param tenantId ID of the user's tenant
     */
    public void validateUser(final String userId, final String tenantId, final String altTenantId);

    /**
     * Retrieve the person's attributes from the attribute repository
     * 
     * @param credentials to lookup in the attribute repository
     * @param failureReason reason why the retrieval failed
     * @return The person's attributes
     */
    public abstract StorageOSUserDAO getStorageOSUser(final Credentials credentials,
            ValidationFailureReason[] failureReason);

    /**
     * Another implementation of getStorageOSUser which throws Exception with error message instead of using failure reason.
     * 
     * @param credentials
     * @return
     */
    public StorageOSUserDAO getStorageOSUser(final Credentials credentials);

    /**
     * Get a map of tenancies a user maps to and the applied user mapping
     * 
     * @param username name of the user
     * @return A map with tenant ID as the key and the applied mapping as the value
     */
    public Map<URI, UserMapping> getUserTenants(String username);

    /**
     * 
     * @param username
     * @param tenantURI
     * @param userMapping
     * @return
     */
    public Map<URI, UserMapping> peekUserTenants(String username, URI tenantURI, List<UserMapping> userMapping);

    /**
     * Set the failure handler which will be invoked when provider connection has issue.
     * @param failureHandler
     */
    public void setFailureHandler(LdapFailureHandler failureHandler);
}
