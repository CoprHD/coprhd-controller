/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.util.Map;

import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.util.NetworkLite;

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

    /**
     * Returns the appropriate assigner for a particular type of storage array
     * that handles assigning ports based on existing zones.
     * 
     * @param deviceType - String corresponding to DiscoverdDataObject.Type enum
     * @param zonesByNetwork existing zones mapped by network and initiator within the network
     * @return StoragePortsAssigner
     */
    public static StoragePortsAssigner getAssignerForZones(String deviceType, Map<NetworkLite, StringSetMap> zonesByNetwork) {
        if (zonesByNetwork == null || zonesByNetwork.isEmpty()) {
            return getAssigner(deviceType);
        } else {
            return new ZonedPortsStoragePortsAssigner(zonesByNetwork);
        }
    }
}
