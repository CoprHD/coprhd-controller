/*
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
