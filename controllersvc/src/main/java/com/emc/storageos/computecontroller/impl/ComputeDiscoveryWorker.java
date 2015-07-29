/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import com.emc.cloud.ucsm.service.UCSMService;
import com.emc.storageos.computecontroller.impl.ucs.UcsDiscoveryWorker;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.plugins.AccessProfile;

public class ComputeDiscoveryWorker {

    private UCSMService ucsmService;
    private DbClient dbClient;

    public ComputeDiscoveryWorker(UCSMService _ucsmService, DbClient _dbClient) {
        ucsmService = _ucsmService;
        dbClient = _dbClient;
    }

    /*
     * Determines the device and call the appropriate discovery worker
     * based on the access provider.
     */
    public void discoverComputeSystem(AccessProfile accessProfile) throws Exception {
        // Look for the system type and kick off discovery.
        if (accessProfile.getSystemType().equals(DiscoveredDataObject.Type.ucs.name())) {
            UcsDiscoveryWorker ucsDiscoveryWorker = new UcsDiscoveryWorker(ucsmService, dbClient);
            ucsDiscoveryWorker.discoverComputeSystem(accessProfile.getSystemId());
        }
    }
}
