/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.utils;

import com.emc.storageos.db.client.model.StorageSystem;

public interface CapacityCalculator {
    /**
     * Calculates the actual allocated capacity on the storage system
     * for the given requested capacity.
     * 
     * @param requestedCapacity the user requested volume capacity
     * @return the actually array allocated capacity
     */
    public Long calculateAllocatedCapacity(Long requestedCapacity, StorageSystem storageSystem);

    /**
     * Determines if the requested capacity between the storage system
     * passed in and the one invoking this method can match.
     * 
     * @param storageSystemType
     * @return Boolean indicating if they can match
     */
    public Boolean capacitiesCanMatch(String storageSystemType);
}
