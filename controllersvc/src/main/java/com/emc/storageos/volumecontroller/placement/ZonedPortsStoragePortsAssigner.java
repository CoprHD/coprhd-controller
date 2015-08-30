/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.util.NetworkLite;

/**
 * Assign StoragePorts to Initiators from an existing list of pre-zoned ports.
 * This class contains the default implementation, which is one storage port
 * assigned to each initiator. It is used for the VMAX.
 * 
 */
public class ZonedPortsStoragePortsAssigner extends DefaultStoragePortsAssigner {
    protected static final Logger _log = LoggerFactory
            .getLogger(ZonedPortsStoragePortsAssigner.class);

    private Map<NetworkLite, StringSetMap> zonesByNetwork = null;

    ZonedPortsStoragePortsAssigner(Map<NetworkLite, StringSetMap> zonesByNetwork) {
        super();
        this.zonesByNetwork = zonesByNetwork;
    }

    @Override
    public boolean isPortAssignableToInitiator(NetworkLite initiatorNetwork, Initiator initiator, StoragePort port) {
        StringSetMap zoneMap = zonesByNetwork.get(initiatorNetwork);
        StringSet initiatorZonedPorts = zoneMap.get(initiator.getId().toString());
        if (initiatorZonedPorts != null && !initiatorZonedPorts.isEmpty()) {
            for (String portId : initiatorZonedPorts) {
                if (portId.equals(port.getId().toString())) {
                    return true;
                }
            }
        }
        return false;
    }
}
