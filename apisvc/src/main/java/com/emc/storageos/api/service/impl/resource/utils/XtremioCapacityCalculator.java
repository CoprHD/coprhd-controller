/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.StorageSystem;

public class XtremioCapacityCalculator implements CapacityCalculator {
    /**
     * {@inheritDoc}
     */
    @Override
    public Long calculateAllocatedCapacity(Long requestedCapacity, StorageSystem storageSystem) {
        // 1 MB is added to make up the missing bytes due to divide by 1024
        Long capacityInMB = new Long(requestedCapacity / (1024 * 1024) + 1);
        return (capacityInMB * 1024 * 1024);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean capacitiesCanMatch(String storageSystemType) {
        if (storageSystemType.equalsIgnoreCase(DiscoveredDataObject.Type.vmax.name())) {
            return false;
        }
        return true;
    }

}
