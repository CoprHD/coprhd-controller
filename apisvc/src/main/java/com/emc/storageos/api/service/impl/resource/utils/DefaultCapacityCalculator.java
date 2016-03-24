/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Volume;

public class DefaultCapacityCalculator implements CapacityCalculator {
    private String systemType = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public Long calculateAllocatedCapacity(Long requestedCapacity, Volume volume, DbClient dbClient) {
        return requestedCapacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Boolean capacitiesCanMatch(String storageSystemType) {
        if (this.systemType.equalsIgnoreCase(DiscoveredDataObject.Type.vnxblock.toString())) {
            return true;
        }
        return false;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

}
