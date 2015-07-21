/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
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
