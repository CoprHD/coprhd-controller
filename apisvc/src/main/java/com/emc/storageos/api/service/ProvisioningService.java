/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.api.service;

/**
 * The main API for provisioning service lifecycle management
 */
public interface ProvisioningService {
    /**
     * Starts provisioning service and registers with coordinator cluster
     * 
     * @throws Exception
     */
    public void start() throws Exception;

    /**
     * Unregisters from coordinator cluster and stops provisioning service
     * 
     * @throws Exception
     */
    public void stop() throws Exception;
}
