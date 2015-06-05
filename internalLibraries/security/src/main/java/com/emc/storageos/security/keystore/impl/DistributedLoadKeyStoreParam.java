/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2013-2014 EMC Corporation All Rights Reserved
 * 
 * This software contains the intellectual property of EMC Corporation or is licensed to
 * EMC Corporation from third parties. Use of this software and the intellectual property
 * contained therein is expressly limited to the terms and conditions of the License
 * Agreement under which it is provided by or on behalf of EMC.
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
