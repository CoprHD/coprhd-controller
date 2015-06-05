/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.systemservices;

/*
 * API to handle starting and stopping the service, as well as performing upgrades.
 */
public interface SysSvc {

    /**
     * Starts Upgrade Coordinator Service
     *
     * @throws Exception
     */
    public void start() throws Exception;

    /**
     * Stops Upgrade Coordinator Service
     *
     * @throws Exception
     */
    public void stop() throws Exception;

    /**
     * Fetch Upgrade URL
     * @return              Returns string containing URL containing upgrade OVF files.
     * @throws Exception
    public String fetchUpgradeOVFURL() throws Exception;
     */
}
