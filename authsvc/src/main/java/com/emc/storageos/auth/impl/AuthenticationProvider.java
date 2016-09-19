/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.auth.impl;

import com.emc.storageos.auth.StorageOSAuthenticationHandler;
import com.emc.storageos.auth.StorageOSPersonAttributeDao;
import com.emc.storageos.db.client.model.AuthnProvider;

/**
 * This class pairs an authentication handler and an attribute repository
 * 
 * */
public class AuthenticationProvider {

    private final StorageOSAuthenticationHandler _handler;
    private final StorageOSPersonAttributeDao _attributeRepository;
    private AuthnProvider providerConfig;

    public AuthenticationProvider(final StorageOSAuthenticationHandler handler,
                                  final StorageOSPersonAttributeDao attributeRepository,
                                  final AuthnProvider providerConfig) {
        _handler = handler;
        _attributeRepository = attributeRepository;
        this.providerConfig = providerConfig;
    }

    public StorageOSAuthenticationHandler getHandler() {
        return _handler;
    }

    public StorageOSPersonAttributeDao getAttributeRepository() {
        return _attributeRepository;
    }

    public AuthnProvider getProviderConfig() {
        return providerConfig;
    }

    public void setProviderConfig(AuthnProvider providerConfig) {
        this.providerConfig = providerConfig;
    }
}
