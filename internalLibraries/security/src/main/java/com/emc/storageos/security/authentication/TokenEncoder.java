/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 *
 *  software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of 
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.security.authentication;

import com.emc.storageos.security.exceptions.SecurityException;


/**
 *  Interface to define the required functionality to encode/decode tokens
 *  on the wire 
 */
public interface TokenEncoder {
    
    /**
     * Encodes the supplied token
     * @param rawToken
     * @return encodedToken
     * @throws SecurityException
     */
    public String encode(TokenOnWire rawToken) throws SecurityException;
    
    /**
     * Decodes the supplied Token
     * @param encodedToken
     * @return rawToken
     * @throws SecurityException
     */
    public TokenOnWire decode(String encodedToken) throws SecurityException;
}
