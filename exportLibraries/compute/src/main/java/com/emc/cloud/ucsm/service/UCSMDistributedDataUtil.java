/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.cloud.ucsm.service;

import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.DistributedDataManager;
import com.emc.storageos.coordinator.client.service.impl.CoordinatorClientImpl;

public class UCSMDistributedDataUtil {

    @Autowired
    private CoordinatorClientImpl coordinator;

    private DistributedDataManager distributedDataManager;

    /**
     * @return the distributedDataManager
     */
    public DistributedDataManager getDistributedDataManager() {
        return distributedDataManager;
    }

    /**
     * @param coordinator the coordinator to set
     */
    public void setCoordinator(CoordinatorClientImpl coordinator) {
        this.coordinator = coordinator;
    }

    public void initDataManager() {
        distributedDataManager = coordinator
                .createDistributedDataManager(ComputeSessionUtil.Constants.COMPUTE_SESSION_BASE_PATH.toString());
    }
}
