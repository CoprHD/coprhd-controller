/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;

public class VPlexBackEndOrchestratorUtil {
    private static final Logger _log = LoggerFactory.getLogger(VPlexBackEndOrchestratorUtil.class);

    public static List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested, NetworkLite net, URI varrayURI,
            boolean simulation, BlockStorageScheduler blockScheduler, DbClient dbClient) {
        Collections.shuffle(candidatePorts);
        if (simulation) {
            StoragePortsAllocator.PortAllocationContext context = StoragePortsAllocator
                    .getPortAllocationContext(net, "arrayX", allocator.getContext());
            for (StoragePort port : candidatePorts) {
                context.addPort(port, null, null, null, null);
            }
            List<StoragePort> portsAllocated = allocator.allocatePortsForNetwork(portsRequested,
                    context, false, null, false);
            allocator.setContext(context);
            return portsAllocated;
        } else {
            Map<StoragePort, Long> sportMap = blockScheduler
                    .computeStoragePortUsage(candidatePorts);
            List<StoragePort> portsAllocated = allocator.selectStoragePorts(dbClient, sportMap,
                    net, varrayURI, portsRequested, null, false);
            return portsAllocated;
        }
    }

    public static StringSetMap configureZoning(Map<URI, List<StoragePort>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup, Map<URI, NetworkLite> networkMap) {
        StringSetMap zoningMap = new StringSetMap();
        // Set up indexes for each of the Networks.
        Map<URI, Integer> networkIndexes = new HashMap<URI, Integer>();
        for (URI networkURI : portGroup.keySet()) {
            networkIndexes.put(networkURI, new Integer(0));
        }
        // Iterate through each of the directors, matching each of its
        // initiators
        // with one port. This will ensure not to violate four paths per
        // director.
        for (String director : initiatorGroup.keySet()) {
            for (URI networkURI : initiatorGroup.get(director).keySet()) {
                NetworkLite net = networkMap.get(networkURI);
                for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                    // If there are no ports on the initiators network, too
                    // bad...
                    if (portGroup.get(networkURI) == null) {
                        _log.info(String.format("%s -> no ports in network",
                                initiator.getInitiatorPort()));
                        continue;
                    }
                    // Round robin through the ports.
                    Integer index = networkIndexes.get(networkURI);
                    StoragePort storagePort = portGroup.get(networkURI).get(index);
                    _log.info(String.format("%s %s   %s -> %s  %s", director, net.getLabel(),
                            initiator.getInitiatorPort(), storagePort.getPortNetworkId(),
                            storagePort.getPortName()));
                    StringSet ports = new StringSet();
                    ports.add(storagePort.getId().toString());
                    zoningMap.put(initiator.getId().toString(), ports);
                    if (++index >= portGroup.get(networkURI).size()) {
                        index = 0;
                    }
                    networkIndexes.put(networkURI, index);
                }
            }
        }
        return zoningMap;
    }

}
