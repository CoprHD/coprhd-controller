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
 * Assign StoragePorts to Initiators.
 * There are subclasses corresponding to specific array types.
 * This class contains the default implementation, which is one storage port
 * assigned to each initiator. It is used for the VMAX.
 * 
 * @author watsot3
 */
public class ZonedPortsStoragePortsAssigner extends DefaultStoragePortsAssigner {
    protected static final Logger _log = LoggerFactory
            .getLogger(ZonedPortsStoragePortsAssigner.class);

    private Map<NetworkLite, StringSetMap> zonesByNetwork = null;

    ZonedPortsStoragePortsAssigner(Map<NetworkLite, StringSetMap> zonesByNetwork) {
        super();
        this.zonesByNetwork = zonesByNetwork;
    }

    /**
     * Assign the ports to a set of new hosts. Ensure the assignment is made from
     * pre-zoned ports only.
     * 
     * @param assignments OUTPUT Map of list of StoragePorts representing the assignments
     * @param storagePorts INPUT the ports that can be used for assignment
     * @param pathsPerInitiator INPUT the desired number of paths per initiator
     * @param hostInitiatorsMap INPUT a map of Host URI to the Initiators in that host
     */
    protected void assignPortsToHosts(
            Map<Initiator, List<StoragePort>> assignments,
            List<StoragePort> storagePorts,
            int pathsPerInitiator, Map<URI,
            List<Initiator>> hostInitiatorsMap,
            NetworkLite initiatorNetwork) {

        // Assign the ports of each new host.
        // This is the default implementation, allocating pathsPerInitiator paths
        // to each port.
        // The assignment strategy here can be simple... we have already picked the
        // correct number of ports given maxPaths. We assign the initiators to ports
        // by hosts so that if we reuse ports in the assignment, hopefully it will be
        // across different hosts, and not within the same host.
        // This code is dependent on the fact that the allocator returns a list with
        // that as you progress sequentially down the list you get alternation of
        // engines / directors, etc. This means that a given host should allocate ports
        // that are adjacent in the list.
        for (URI host : hostInitiatorsMap.keySet()) {
            int portIndex = 0;
            // Cycle through the initiators within a host until we've used all ports if necessary.
            // If there are more ports than initiators in the host then the additional will be
            // used for a different host. If there are more initiators than ports, the excess
            // initiators will be ignored.
            for (Initiator initiator : hostInitiatorsMap.get(host)) {
                if ((portIndex + pathsPerInitiator) <= storagePorts.size()) {
                    if (assignments.get(initiator) == null) {
                        assignments.put(initiator, new ArrayList<StoragePort>());
                    }
                    // Allocate contiguously the ports for one initiator
                    for (int i = 0; i < pathsPerInitiator; i++) {
                        StoragePort port = storagePorts.get(portIndex);
                        if (!assignments.get(initiator).contains(port) &&
                                isZonedToInitiator(initiatorNetwork, initiator, port)) {
                            assignments.get(initiator).add(port);
                            _log.info(String.format("Port %s assigned to initiator %s host %s",
                                    BlockStorageScheduler.portName(port), initiator.getInitiatorPort(), host));
                            portIndex++;
                        }
                    }
                } else {
                    _log.info(String.format("No assignments for initiator %s (%s)",
                            initiator.getInitiatorPort(), initiator.getId()));
                }
            }
        }
    }

    /**
     * Checks the existing zones to determine if the port is pre-zoned to the initiator
     * 
     * @param initiatorNetwork -- the initiator network
     * @param initiator -- the initiators
     * @param port -- the port to be checked
     * @return true if there is an existing zone between the port and the initiator
     */
    private boolean isZonedToInitiator(NetworkLite initiatorNetwork, Initiator initiator, StoragePort port) {
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
