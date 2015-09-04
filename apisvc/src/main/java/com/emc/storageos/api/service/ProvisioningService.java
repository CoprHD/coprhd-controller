/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
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
