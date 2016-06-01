/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth;

import com.emc.storageos.auth.impl.LdapFailureHandler;
import org.apache.commons.httpclient.Credentials;

public interface StorageOSAuthenticationHandler {

    /**
     * Authenticate the given user credentials
     * 
     * @param credentials credential to authenticate
     * @return true if the user authenticates
     */
    public boolean authenticate(final Credentials credentials);

    /**
     * Determine if the credentials are supported by this authentication handler
     * 
     * @param credentials
     * @return true if the credentials are supported
     */
    public boolean supports(final Credentials credentials);

    /**
     * Set the failure handler which will be invoked when provider connection has issue.
     * @param failureHandler
     */
    public void setFailureHandler(LdapFailureHandler failureHandler);
}
