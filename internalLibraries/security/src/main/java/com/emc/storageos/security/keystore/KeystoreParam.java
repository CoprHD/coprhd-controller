/*
 * Copyright (c) 2013-2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.security.keystore;

import java.security.KeyStore.LoadStoreParameter;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;

/**
 * Represents the parameters needed for loading a ViPR keystore (not depending on the data
 * storing approach)
 */
public interface KeystoreParam extends LoadStoreParameter {

    /**
     * the coordinator client. Needed to get information about the cluster
     * 
     * @return the CoordinatorClient
     */
    public CoordinatorClient getCoordinator();
}
