/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.util.NetworkLite;

public interface StoragePortsAssigner {

    /**
     * Returns the number of ports that should be allocated for each path.
     * 
     * @return Integer.
     */
    public abstract int getNumberOfPortsPerPath();

    public static final URI unknown_host_uri = URI.create("unknown_host");

    /**
     * Given a map of Network URI to a List of Initiators in that network,
     * determine the number of ports that needs to be allocated for each
     * network.
     * In the default implementation, one port for each initiator
     * is "ideal" if under num_paths. But we never allocate more ports than
     * initiators for a given network, and the sum of all ports must be <= numPaths.
     * 
     * @param net2InitiatorsMap -- contains a mapping from the Network URI to the new
     *            Initiators. (Initiators previously present in the Export Mask are not included).
     * @param pathParams - Export Path Params (maxPaths, pathsPerInitiator)
     * @param existingPortsMap - map of Network URI to set of existing (already allocated) StoragePorts
     * @param existingInitiatorsMap map of Network URI to set of existing (already assigned) Initiators
     * @return Map of network URI to Integer number of ports to allocate
     */
    public abstract Map<URI, Integer> getPortsNeededPerNetwork(
            Map<URI, List<Initiator>> net2InitiatorsMap,
            ExportPathParams pathParams,
            Map<URI, Set<StoragePort>> existingPortsMap,
            Map<URI, Set<Initiator>> existingInitiatorsMap)
            throws PlacementException;

    /**
     * Assigns the storage ports within a single Network to the Initiators.
     * This is called once for each Network used during an export.
     * One port is assigned to each initiator.
     * 
     * @param assignments OUTPUT map of Initiator to the List<StoragePort> storage ports
     *            to be zoned with that initiator.
     * @param initiators List<Initiator> new initiators being assigned.
     *            Initiators previously assigned that are already in the ExportMask are not included.
     * @param storagePorts List<StoragePort> the allocated storage ports to be assigned
     *            This can include newly allocated ports, and some that were previously assigned.
     *            They are in order previously assigned first, then newly assigned ports.
     * @param pathParams - Export Path Params (maxPaths, pathsPerInitiator)
     * @param initiatorNetwork the network of the initiator
     * @param existingAssignments- previously existing assignments map of Initiator to list of StoragePorts
     */
    public abstract void assign(Map<Initiator, List<StoragePort>> assignments,
            List<Initiator> initiators, List<StoragePort> storagePorts,
            ExportPathParams pathParams,
            Map<Initiator, List<StoragePort>> existingAssignments, NetworkLite initiatorNetwork);

    /**
     * Sub-class specific implementation for checking if the port can be assigned to the initiator.
     * For example, when assigning from the list of pre-zoned ports, this check ensures that a zone
     * exists between the pre-zoned port and the initiator.
     * 
     * @param initiatorNetwork -- the initiator's network
     * @param initiator -- the initiator
     * @param port -- the port
     * @return if the port can be used for this initiator.
     */
    public boolean isPortAssignableToInitiator(NetworkLite initiatorNetwork, Initiator initiator, StoragePort port);
}
