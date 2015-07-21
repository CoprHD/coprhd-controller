/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
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
