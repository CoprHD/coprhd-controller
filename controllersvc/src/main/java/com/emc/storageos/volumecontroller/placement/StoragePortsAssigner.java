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
     * @param networkOrder -- output parameter List of networks in order they should be allocated.
     * @return Map of network URI to Integer number of ports to allocate
     */
    public abstract Map<URI, Integer> getPortsNeededPerNetwork(
            Map<URI, List<Initiator>> net2InitiatorsMap,
            ExportPathParams pathParams,
            Map<URI, Set<StoragePort>> existingPortsMap,
            Map<URI, Set<Initiator>> existingInitiatorsMap,
            List<URI> networkOrder)
            throws PlacementException;

    /**
     * Assign storage ports for one host across all networks.
     * @param assignments OUTPUT map of Initiator to the List<StoragePort> storage ports
     *            to be zoned with that initiator.
     * @param netToInitiators - a map of network URI to Initiators in that network
     * @param netToAllocatedPorts - a map of network URI to a List of Allocated Storage Ports
     * @param ExportPathParams - holder for the path parameters
     * @param Map<Initiator, List<StoragePort>> existingAssignments
     * @param URI hostURI -- host URI we are assigning for
     * @param initiatorToNetworkLiteMap map of Initiator to NetworkLite object 
     *      (can be null for unit tests, only used to evaluate prezoning)
     */
    public abstract void assignPortsToHost(Map<Initiator, List<StoragePort>> assignments, 
            Map<URI, List<Initiator>> netToNewInitiators, Map<URI, List<StoragePort>> netToAllocatedPorts,
            ExportPathParams pathParams, Map<Initiator, List<StoragePort>> existingAssignments, URI hostURI,
            Map<Initiator, NetworkLite> initiatorToNetworkLiteMap); 

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
