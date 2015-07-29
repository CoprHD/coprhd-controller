/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

public class StoragePortsAssignerFactory {
    /**
     * Returns the appropriate assigner for a particular type of storage array.
     * 
     * @param deviceType - String corresponding to DiscoverdDataObject.Type enum
     * @return StoragePortsAssigner
     */
    public static StoragePortsAssigner getAssigner(String deviceType) {
        return new DefaultStoragePortsAssigner();
    }
}
