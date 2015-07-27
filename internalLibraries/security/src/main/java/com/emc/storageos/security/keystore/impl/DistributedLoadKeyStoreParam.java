/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore.impl;

import java.security.KeyStore.ProtectionParameter;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.security.keystore.KeystoreParam;

/**
 * THe parameters needed to load a keystore that'll use coordinator to store the data.
 */
public class DistributedLoadKeyStoreParam implements KeystoreParam {

    private CoordinatorClient coordinator;

    @Override
    public ProtectionParameter getProtectionParameter() {
        return null;
    }

    /**
     * @return the coordiantor
     */ 
    @Override
    public CoordinatorClient getCoordinator() {
        return coordinator;
    }

    /**
     * @param coordiantor
     *            the coordiantor to set
     */ 
    public void setCoordinator(CoordinatorClient coordiantor) {
        this.coordinator = coordiantor;
    }

}
