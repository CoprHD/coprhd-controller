/**
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

import org.apache.commons.httpclient.Credentials;

public interface StorageOSAuthenticationHandler {

    /**
     * Authenticate the given user credentials
     * @param credentials credential to authenticate
     * @return true if the user authenticates
     */
    public boolean authenticate(final Credentials credentials);
    
    /**
     * Determine if the credentials are supported by this authentication handler
     * @param credentials
     * @return true if the credentials are supported
     */
    public boolean supports(final Credentials credentials);
}
