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
package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.StorageOSAuthenticationHandler;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;

/**
 *  This class pairs an authentication handler and an attribute repository
 * 
 *  */
public class AuthenticationProvider {

    private final StorageOSAuthenticationHandler _handler;
    private final StorageOSPersonAttributeDao _attributeRepository;    
    
    public AuthenticationProvider( final StorageOSAuthenticationHandler handler, final StorageOSPersonAttributeDao attributeRepository) {
        _handler = handler;
        _attributeRepository = attributeRepository;
    }    
    
    public StorageOSAuthenticationHandler getHandler() {
        return _handler;
    }
    
    public StorageOSPersonAttributeDao getAttributeRepository() {
        return _attributeRepository;
    }
}
