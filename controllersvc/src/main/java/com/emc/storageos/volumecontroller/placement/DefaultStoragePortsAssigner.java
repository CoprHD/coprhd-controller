/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.NetworkLite;

/**
 * Assign StoragePorts to Initiators.
 * There are subclasses corresponding to specific array types.
 * This class contains the default implementation, which is one storage port
 * assigned to each initiator. It is used for the VMAX.
 *
 * @author watsot3
 */
public class DefaultStoragePortsAssigner implements StoragePortsAssigner {

    protected static final Logger _log = LoggerFactory
            .getLogger(DefaultStoragePortsAssigner.class);

    @Override
    public int getNumberOfPortsPerPath() {
        return 1;
    }

    /*
     * (non-Javadoc)
     *
     * @see com.emc.storageos.volumecontroller.placement.StoragePortsAssigner#getPortsNeededPerNetwork(java.util.Map, int)
     */
    @Override
    public Map<URI, Integer> getPortsNeededPerNetwork(
            Map<URI, List<Initiator>> net2InitiatorsMap,    // new initiators only grouped by Network
            ExportPathParams pathParams,
            Map<URI, Set<StoragePort>> existingPortsMap,    // existing ports grouped by Network
            Map<URI, Set<Initiator>> existingInitiatorsMap, // existing initiators grouped by Network
            boolean usingSwitchAffinity,
            List<URI> networkOrder)                     // OUTPUT; order for network allocations
            throws PlacementException {
        if (existingPortsMap == null) {
            existingPortsMap = new HashMap<URI, Set<StoragePort>>();
        }
        if (existingInitiatorsMap == null) {
            existingInitiatorsMap = new HashMap<URI, Set<Initiator>>();
        }

        Integer allocated = 0;
        Map<URI, Integer> net2NumPortsMap = new HashMap<URI, Integer>();

        // Collect all the existing initiators by network.
        Map<URI, Set<Initiator>> allInitiatorsMap = new HashMap<URI, Set<Initiator>>();
        allInitiatorsMap.putAll(existingInitiatorsMap);
        // Add in the initiators to be newly allocated.
        for (URI netURI : net2InitiatorsMap.keySet()) {
            List<Initiator> initiators = net2InitiatorsMap.get(netURI);
            if (initiators == null || initiators.isEmpty()) {
                continue;
            }
            if (allInitiatorsMap.get(netURI) == null) {
                allInitiatorsMap.put(netURI, new HashSet<Initiator>());
            }
            allInitiatorsMap.get(netURI).addAll(initiators);
        }

        // Get the network URIs from all the initiators.
        URI[] networkURIs = new URI[0];
        networkURIs = allInitiatorsMap.keySet().toArray(networkURIs);

        // Get the maximum number of initiators in a single host for each given Network.
        // This is the combination of the existing initiators and the new initiators.
        Map<URI, Integer> net2MaxHostInitiators = makeNetwork2MaxHostInitiators(
                allInitiatorsMap);

        // Determine the non-redundant networks. These are the networks where at least
        // one host is using that single network. For non redundant networks, we
        // will allow the lower of maxPaths or the maximum initiators for any host..
        // If a network is redundant, we will allow the lower of (maxPaths+1)/2 or the
        // maximum number of initiators for any host.
        Map<URI, Set<URI>> hostToNetworksMap = new HashMap<URI, Set<URI>>();
        Set<URI> nonRedundantNetworks = networksNotRedundant(allInitiatorsMap, hostToNetworksMap);

        // Generate network allocation ordering.
        networkOrder.clear();
        networkOrder.addAll(orderNetworksForAllocation(hostToNetworksMap, net2MaxHostInitiators));

        // Strategy: we iterate over the networks, adding pathsPerInitiator ports at a time
        // to each network that has fewer paths than maxHostInitiators * pathsPerInitiator
        // and that has not exceeded the pathLimit.
        // Continue until do not add any more ports to the request for any network.
        int maxPaths = pathParams.getMaxPaths();
        int pathsPerInitiator = pathParams.getPathsPerInitiator();
        Integer initiatorsPerPort = pathParams.getMaxInitiatorsPerPort();
        boolean addedThisPass;
        do {
            addedThisPass = false;
            for (URI networkURI : networkURIs) {
                _log.debug("Processing network " + networkURI);
                // If a nonRedundantNet, then at least one host is using only this network,
                // so we set an upper bound of maxPaths on the number of ports requested.
                // If we are using switch affinity, we need additional ports to allow matching
                // of port switch to initiator switch.
                // Otherwise we know there is at least one other network with initiators
                // so set an upper bound of (max_paths+1)/2 which will assume we won't need
                // more than half the ports in this network. We round up in the case of
                // odd max_paths as we aren't sure which network should receive the
                // extra port.
                // The path limit keeps us from allocating a ridiculously large number of
                // ports that won't be used when assigning as that reduces the chances for
                // redundancy between networks in the Storage Ports Allocator.
                boolean nonRedundantNet = nonRedundantNetworks.contains(networkURI);
                int pathLimit = (nonRedundantNet ? maxPaths : ((maxPaths + pathsPerInitiator) / 2));
                if (pathLimit < pathsPerInitiator) {
                    pathLimit = pathsPerInitiator;
                }
                
                // We also get the maxHostInitiators for this network.
                // We never need to allocate more ports than maxHostInitiators * pathsPer_initiator
                // for a given network.
                int maxHostInitiators = net2MaxHostInitiators.get(networkURI);
                if (net2NumPortsMap.get(networkURI) == null) {
                    net2NumPortsMap.put(networkURI, new Integer(0));
                }
                // If we currently have fewer paths in our network
                // than maxHostInitiators * pathsPerInitiator,
                // and we are under the pathLimit, add a paths_per_initiator ports
                // to our request for this network.
                Integer currentPorts = net2NumPortsMap.get(networkURI);
                _log.info(String.format("Network %s pathLimit %d maxHostInitiators %d currentPorts %d",
                        networkURI, pathLimit, maxHostInitiators, currentPorts));
                if (currentPorts <= (pathLimit - pathsPerInitiator)
                        && currentPorts < (maxHostInitiators * pathsPerInitiator)) {
                    net2NumPortsMap.put(networkURI, currentPorts + pathsPerInitiator);
                    addedThisPass = true;
                    allocated += pathsPerInitiator;
                }
            }
        } while (addedThisPass);

        // If initiators per port is > 1, scale number of ports needed down appropriately,
        // as long as we result in allocating at least paths pere initiator ports
        if (initiatorsPerPort > 1 && !usingSwitchAffinity) {
            for (Map.Entry<URI, Integer> entry : net2NumPortsMap.entrySet()) {
                Double d = Math.ceil(entry.getValue().doubleValue() / initiatorsPerPort.doubleValue());
                if (d.intValue() >= pathsPerInitiator) {
                    entry.setValue(Integer.valueOf(d.intValue()));
                }
            }
        }

        // Check that we are above minPaths
        if (allocated < pathParams.getMinPaths()) {
            int initiatorCount = 0;
            for (Set<Initiator> initSet : allInitiatorsMap.values()) {
                initiatorCount += getInitiatorCount(new ArrayList(initSet));
            }
            _log.info(String.format("Could not request min_path number of ports: ports needed %d "
                    + "total initiators %d paths_per_initiator %d min_paths %d max_paths %d",
                    allocated, initiatorCount, pathParams.getPathsPerInitiator(),
                    pathParams.getMinPaths(), pathParams.getMaxPaths()));
            throw PlacementException.exceptions.cannotAllocateMinPaths(allocated, initiatorCount,
                    pathParams.getPathsPerInitiator(), pathParams.getMinPaths(), pathParams.getMaxPaths());
        }

        return net2NumPortsMap;
    }

    /**
     * Sorts the existing ports by the way they were previously grouped in
     * existingAssignments, with initiators having the most ports first and the
     * least ports last. Finally adds in the newly allocated ports and
     * returns them in newPorts.
     *
     * @param storagePorts list of StoragePorts returned by allocator
     * @param existingAssignments Map of Initiator to already allocated Ports
     * @param newPorts OUT parameter containing the newly added ports.
     * @return
     */
    private List<StoragePort> sortPorts(List<StoragePort> storagePorts,
            Map<Initiator, List<StoragePort>> existingAssignments,
            List<StoragePort> newPorts) {
        Set<URI> includedPorts = new HashSet<URI>();
        List<StoragePort> sortedPorts = new ArrayList<StoragePort>();
        // Construct a map of the stoarge ports to simplify lookup
        Map<URI, StoragePort> portsMap = DataObjectUtils.toMap(storagePorts);

        // Arrange the storagePorts based on the existing assignments.
        // Start with initiators that have multiple port assignments first.
        // The idea is that they should be kept together for proper redundancy.
        for (int numPorts = 4; numPorts >= 1; numPorts--) {
            for (List<StoragePort> ports : existingAssignments.values()) {
                if (ports.size() >= numPorts) {
                    for (StoragePort port : ports) {
                        if (!includedPorts.contains(port.getId())
                                && portsMap.containsKey(port.getId())) {
                            sortedPorts.add(port);
                            includedPorts.add(port.getId());
                        }
                    }
                }
            }
        }

        // Add any newly allocated ports.
        for (StoragePort port : storagePorts) {
            if (!includedPorts.contains(port.getId())) {
                sortedPorts.add(port);
                includedPorts.add(port.getId());
                newPorts.add(port);
            }
        }
        if (sortedPorts.size() != storagePorts.size()) {
            _log.error("sortPorts size incorrect");
            _log.error(sortedPorts.toString());
            _log.error(storagePorts.toString());
            return null;
        }
        return sortedPorts;
    }

    /**
     * Returns a map from Host URI to the List<Initiator> list of initiators on that host.
     * We process by host in the outer-most loop so that if ports have to be shared,
     * they are shared across different hosts.
     *
     * @param initiators List<Initiator>
     * @return Map<URI, List<Initiator>> map of host URI to that host's initiators
     */
    static public Map<URI, List<Initiator>> makeHostInitiatorsMap(Collection<Initiator> initiators) {
        Map<URI, List<Initiator>> hostInitiatorsMap = new HashMap<URI, List<Initiator>>();
        for (Initiator initiator : initiators) {
            URI host = initiator.getHost();
            if (NullColumnValueGetter.isNullURI(host)) {
                host = StoragePortsAssigner.unknown_host_uri;
            }
            if (hostInitiatorsMap.containsKey(host) == false) {
                hostInitiatorsMap.put(host, new ArrayList<Initiator>());
            }
            hostInitiatorsMap.get(host).add(initiator);
        }
        return hostInitiatorsMap;
    }

    /**
     * Sums the existing port counts per network into the net2NumPortsMap.
     *
     * @param net2NumPortsMap - will contain the total with the addition of net2NumExistingPortsMap
     * @param net2NumExistingPortsMap
     */
    protected void sumPortMaps(Map<URI, Integer> net2NumPortsMap, Map<URI, Integer> net2NumExistingPortsMap) {
        for (URI networkURI : net2NumPortsMap.keySet()) {
            Integer existing = net2NumExistingPortsMap.get(networkURI);
            if (existing != null) {
                Integer sum = existing + net2NumPortsMap.get(networkURI);
                net2NumPortsMap.put(networkURI, sum);
            }
        }
    }

    /**
     * Calculates the maximum number of host initiators in a single host within each Network.
     *
     * @param net2InitiatorsMap - map of Network to list of Host Initiators in that Network
     * @return
     */
    private Map<URI, Integer> makeNetwork2MaxHostInitiators(
            Map<URI, Set<Initiator>> net2InitiatorsMap) {
        Map<URI, Integer> net2MaxHostInitiators = new HashMap<URI, Integer>();
        for (URI net : net2InitiatorsMap.keySet()) {
            Map<URI, List<Initiator>> hostInitiatorsMap =
                    makeHostInitiatorsMap(net2InitiatorsMap.get(net));
            int max = 0;
            for (URI host : hostInitiatorsMap.keySet()) {
                List<Initiator> list = hostInitiatorsMap.get(host);
                int thisHost = getInitiatorCount(list);
                max = (thisHost > max) ? thisHost : max;
            }
            net2MaxHostInitiators.put(net, max);
            _log.info(String.format("Network %s max initiators per host %d", net.toString(), max));
        }
        return net2MaxHostInitiators;
    }

    /**
     * Determines if there are any hosts that only have connectivity to only one network.
     * We favor allocating more ports in a network if it is the only one some host has access to.
     * Returns a list of such networks.
     *
     * @param net2InitiatorsMap -- a map of Network URI to a set of Initiator objects in that network
     * @param hostToNetworks - outputs a map of Host URI to Netowrk URIs used by that host
     * @return URI set of networks that are not redundant
     */
    private Set<URI> networksNotRedundant(Map<URI, Set<Initiator>> netToInitiatorsMap,
            Map<URI, Set<URI>> hostToNetworks) {
        hostToNetworks.clear();
        // Reverse the netToInitiatorsMap to make an Initiator to Net map.
        Map<Initiator, URI> initiatorsToNetMap = new HashMap<Initiator, URI>();
        for (Map.Entry<URI, Set<Initiator>> entry : netToInitiatorsMap.entrySet()) {
            for (Initiator initiator : entry.getValue()) {
                initiatorsToNetMap.put(initiator, entry.getKey());
            }
        }
        Set<URI> nonredundantNets = new HashSet<URI>();
        Map<URI, List<Initiator>> hostInitiatorsMap = makeHostInitiatorsMap(initiatorsToNetMap.keySet());
        for (Map.Entry<URI, List<Initiator>> entry : hostInitiatorsMap.entrySet()) {
            hostToNetworks.put(entry.getKey(), new HashSet<URI>());
            for (Initiator initiator : entry.getValue()) {
                URI initiatorNet = initiatorsToNetMap.get(initiator);
                if (initiatorNet != null) {
                    hostToNetworks.get(entry.getKey()).add(initiatorNet);
                }
            }
            _log.info(String.format("Host %s uses networks %s", entry.getKey(), hostToNetworks.get(entry.getKey())));
        }
        // Identify any hosts only using one network and add that network to the set.
        for (Set<URI> networks : hostToNetworks.values()) {
            if (networks.size() == 1) {
                _log.info("Non redundant network: " + networks.toString());
                nonredundantNets.addAll(networks);
            }
        }
        return nonredundantNets;
    }

    // A class relating the Network URI to initiator count that can be sorted by initiator count
    // so as to sort the Networks by increasing initiator counts.
    private class NetworkInitiatorCount implements Comparable<NetworkInitiatorCount> {
        private final URI net;
        private final int count;

        NetworkInitiatorCount(URI net, int count) {
            this.net = net;
            this.count = count;
        }

        @Override
        public int hashCode() {
            return net.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (!getClass().equals(obj.getClass()))
                return false;
            NetworkInitiatorCount other = (NetworkInitiatorCount) obj;
            if (net == null) {
                if (other.net != null)
                    return false;
            } else if (!net.equals(other.net))
                return false;
            return true;
        }

        @Override
        public int compareTo(NetworkInitiatorCount arg0) {
            return this.count - arg0.count;
        }
    };

    /**
     * The goal of this algorithm is to order the Networks so as to pass useful context when the Storage Ports Allocator prcesses one
     * network to the network it will process next. So we want networks used by the same host(s) to be processed together, and we
     * defer any Networks that are the single network for a host to be processed at the end if they weren't already added
     * by a host with multiple Networks.
     * Within a host, we want to allocate ports for the Network requiring the least number of ports first, as that will
     * make the fewest hardware unit constraints, thus giving the next network a wider variety of choices.
     *
     * @param hostToNetworks -- a map of Host URI to a set of Network URIs representing the Networks used by this host.
     * @param net2MaxHostInitiators -- a map of Network URI to the maximum number of initiators any one host has on that network.
     *            This is an upper bound on the number of ports needed by the network (when multiplied by paths_per_initiator).
     * @return -- an ordered list of Network URIs representing the order the Storage Ports Allocator should process the
     *         networks
     */
    List<URI> orderNetworksForAllocation(Map<URI, Set<URI>> hostToNetworks, Map<URI, Integer> net2MaxHostInitiators) {
        List<URI> orderedNetworks = new ArrayList<URI>();
        List<URI> skippedNetworks = new ArrayList<URI>();
        // Loop through each host, processing those with more than one network.
        // Record any skipped networks.
        for (Map.Entry<URI, Set<URI>> entry : hostToNetworks.entrySet()) {
            if (entry.getValue().size() == 1) {
                // process single networks last, as that net may be allocated with other net
                skippedNetworks.addAll(entry.getValue());
                continue;
            }
            // May a NetworkInitiatorCount for each network
            List<NetworkInitiatorCount> netInitiatorCounts = new ArrayList<NetworkInitiatorCount>();
            for (URI net : entry.getValue()) {
                netInitiatorCounts.add(new NetworkInitiatorCount(net, net2MaxHostInitiators.get(net)));
            }
            // Sort the netInitiatorCounts list and process from fewest initiators to most.
            // This will give better redundancy choices.
            Collections.sort(netInitiatorCounts);
            List<URI> ordered = new ArrayList<URI>();
            for (NetworkInitiatorCount netInitiatorCount : netInitiatorCounts) {
                ordered.add(netInitiatorCount.net);
                if (!orderedNetworks.contains(netInitiatorCount.net)) {
                    orderedNetworks.add(netInitiatorCount.net);
                }
            }
            _log.info(String.format("host %s ordered nets %s", entry.getKey(), ordered));
        }
        // Add any skipped networks that have not already been processed.
        for (URI skippedNet : skippedNetworks) {
            if (!orderedNetworks.contains(skippedNet)) {
                _log.info("Adding skipped network " + skippedNet);
                orderedNetworks.add(skippedNet);
            }
        }
        _log.info("Ordered networks: " + orderedNetworks);
        return orderedNetworks;
    }

    @Override
    public boolean isPortAssignableToInitiator(NetworkLite initiatorNetwork, Initiator initiator, StoragePort port) {
        return true;
    }

    @Override
    /**
     * The goal of this routine is to handle all the assignments for a single host.
     * Outline of the algorithm for assignPortsToHost.
     * 1. Look at each initiator that has an existing assignment.
     *    a. Validate paths per initiator
     *    b  Tally up the current number of paths currently assigned
     * 2. Loop through Networks, picking unassigned initiator one at a time, and assigning ports.
     *     Repeat until there are no more initiators or we have met max_paths.
     * 3. If there are remaining unconfigured initiators, double up initiators on ports of maxinitiatorsperport is > 1.
     *
     */
    public void assignPortsToHost(Map<Initiator, List<StoragePort>> assignments,
            Map<URI, List<Initiator>> netToNewInitiators, Map<URI, List<StoragePort>> netToAllocatedPorts,
            ExportPathParams pathParams, Map<Initiator, List<StoragePort>> argExistingAssignments,
            URI hostURI, Map<Initiator, NetworkLite> initiatorToNetworkLiteMap, 
            Map<URI, Map<String, List<Initiator>>> switchToInitiatorsByNet,
            Map<URI, Map<String, List<StoragePort>>> switchToStoragePortsByNet) {
        _log.info("Assigning ports for host: " + hostURI);
        
        Map<String, String> portAddressToSwitchName = makePortAddressToSwitchMap(switchToStoragePortsByNet);
        Map<Initiator, String> initiatorToSwitchName = makeInitiatorToSwitchMap(switchToInitiatorsByNet);

        // Make a map of port to the number of initiators using the port.
        Map<StoragePort, Integer> portUseCounts = new HashMap<StoragePort, Integer>();
        // Deal with existingAssignments passed in as null, meaning no assignments
        Map<Initiator, List<StoragePort>> existingAssignments = nonNullAssignmentMap(argExistingAssignments);
        
        //Remove associated initiators if any, so we can assign the storage ports at the end
        Map<Initiator, URI> initiatorToassociatedIniURI = makeInitiatorAssociatedInitiatorPairs(netToNewInitiators);
        Map<URI, Initiator> uriToItsInitiator = makeURIToInitiatorMap(netToNewInitiators, argExistingAssignments);
        removeAssociatedInitiators(initiatorToassociatedIniURI, netToNewInitiators, argExistingAssignments);

        // Determine the Initiators for this particular host.
        Map<URI, List<Initiator>> existingInitiatorsMap = makeHostInitiatorsMap(existingAssignments.keySet());
        List<Initiator> hostExistingInitiators = nonNullInitiatorList(existingInitiatorsMap.get(hostURI));

        // Calculate port use counts from the existing assignments
        for (Initiator hostExistingInitiator : hostExistingInitiators) {
            List<StoragePort> portsAssigned = existingAssignments.get(hostExistingInitiator);
            if (portsAssigned != null) {
                for (StoragePort port : portsAssigned) {
                    _log.info(String.format("Existing assignment initiator %s (%s) port %s (%s/%s) net %s",
                            hostExistingInitiator.getInitiatorPort(), hostExistingInitiator.getHostName(),
                            port.getPortName(), port.getPortNetworkId(),
                            getPortSwitchName(port, portAddressToSwitchName), port.getNetwork()));
                    addPortUse(portUseCounts, port);
                }
            }
        }

        // Put any existing initiators assignments into the assignments.
        assignments.putAll(existingAssignments);

        // If we had existing assignments, sort the allocated ports, getting just the new ports.
        if (!portUseCounts.isEmpty()) {
            for (URI netURI : netToAllocatedPorts.keySet()) {
                List<StoragePort> newPorts = new ArrayList<StoragePort>();
                List<StoragePort> sortedPorts =
                        sortPorts(netToAllocatedPorts.get(netURI), existingAssignments, newPorts);
                netToAllocatedPorts.put(netURI, sortedPorts);
            }
        }

        if (switchToInitiatorsByNet != null && !switchToInitiatorsByNet.isEmpty()) {
            // Pass 1: use only ports with Switch Affinity.
            _log.info("PASS ONE: Assigning initiators with switch affinity");
            iterateAssignmentPasses(assignments, netToNewInitiators, portUseCounts, netToAllocatedPorts,
                    initiatorToSwitchName, portAddressToSwitchName, switchToStoragePortsByNet, initiatorToNetworkLiteMap,
                    pathParams, true, false);

            // Pass 2 - use combination switch affinity and non affinity ports
            _log.info("PASS TWO: Assigning initiators with partial switch affinity");
            iterateAssignmentPasses(assignments, netToNewInitiators, portUseCounts, netToAllocatedPorts,
                    initiatorToSwitchName, portAddressToSwitchName, switchToStoragePortsByNet, initiatorToNetworkLiteMap,
                    pathParams, true, true);
        }

        // Pass 3 - use only non switch affinity
        _log.info("PASS THREE: Assigning initiators without switch affinity");
        iterateAssignmentPasses(assignments, netToNewInitiators, portUseCounts, netToAllocatedPorts,
                initiatorToSwitchName, portAddressToSwitchName, switchToStoragePortsByNet, initiatorToNetworkLiteMap,
                pathParams, false, true);

        // Now if we can map multiple initiators per port, fill in any unprovisoned initiators
        if (pathParams.getMaxInitiatorsPerPort() > 1) {
            int currentStoragePaths = portUseCounts.size();
            _log.info("*** Adding assignments for multiple initiators using same ports, maxInitiatorsPerPort: "

                    + pathParams.getMaxInitiatorsPerPort());
            for (Map.Entry<URI, List<Initiator>> entry : netToNewInitiators.entrySet()) {
                List<StoragePort> allocatedPorts = netToAllocatedPorts.get(entry.getKey());
                // See if we can map the yet unprovisioned initiators to already used ports
                for (Initiator initiator : entry.getValue()) {
                    // If initiator is already provisioned, skip it
                    if (assignments.containsKey(initiator)) {
                        continue;

                    }

                    // Check if provisioning another initiator would put us over max paths
                    if ((currentStoragePaths + pathParams.getPathsPerInitiator()) > pathParams.getMaxPaths()) {
                        break;
                    }

                   /* //try to assigning port form its associated initiator from assignments
                    boolean assigned = assignSamePortsFromAssociateInitiator(initiator, assignments);
                    if (assigned) {
                        // continue from further consideration for this initiator.
                        continue;
                    }*/
                    
                    // Try to find available ports with switch affinity.
                    List<StoragePort> availPorts = new ArrayList<StoragePort>();
                    String switchName = initiatorToSwitchName.get(initiator);
                    if (switchToStoragePortsByNet != null && switchToStoragePortsByNet.containsKey(entry.getKey())) {
                        if (switchToStoragePortsByNet.get(entry.getKey()).containsKey(switchName)) {
                            List<StoragePort> availSwitchPorts = switchToStoragePortsByNet.get(entry.getKey()).get(switchName);
                            List<StoragePort> affinityPorts = getAvailablePorts(initiator, initiatorToNetworkLiteMap, availSwitchPorts, 
                                    portUseCounts, pathParams.getPathsPerInitiator(), pathParams.getMaxInitiatorsPerPort() -1);
                            if (affinityPorts != null) {
                                availPorts.addAll(affinityPorts);
                            }
                        

                        }
                    }
                    // Allocate paths per initiator number of ports without switch affinity
                    int numberPortsNeeded = pathParams.getPathsPerInitiator() - availPorts.size();
                    List<StoragePort> nonAffinityPorts = getAvailablePorts(initiator, initiatorToNetworkLiteMap,
                            allocatedPorts, portUseCounts, numberPortsNeeded,
                            pathParams.getMaxInitiatorsPerPort() - 1);
                    if (nonAffinityPorts != null) {
                        availPorts.addAll(nonAffinityPorts);
                    }
                    if (availPorts != null && !availPorts.isEmpty()) {
                        // Assign them and update the current storage paths count
                        assignPorts(assignments, entry.getKey(), initiator, availPorts, portUseCounts, 
                                portAddressToSwitchName, initiatorToSwitchName);
                        currentStoragePaths += pathParams.getPathsPerInitiator();
                    } else {
                        _log.info(String.format("No available ports for initiator %s", 
                                initiator.getInitiatorPort()));
                    }
                }
            }
        }
        
        //Assign storage ports to associated initiators
        assignPortsToAssociatedInitiators(uriToItsInitiator, assignments);
        restoreExistingAssignments(uriToItsInitiator, argExistingAssignments);
    }

    /**
     * Restore the the map for associated initiator assignment
     * @param uriToItsInitiator
     * @param argExistingAssignments
     */
    private void restoreExistingAssignments(Map<URI, Initiator> uriToItsInitiator,
            Map<Initiator, List<StoragePort>> argExistingAssignments) {
        
        assignPortsToAssociatedInitiators(uriToItsInitiator, argExistingAssignments);
    }

    /**
     * Creates a map of URI to its Initiator object
     * This will be used to retrieve the associated initiator to
     * do the storage ports assignment.
     * 
     * @param netToNewInitiators
     * @param argExistingAssignments
     * @return
     */
    private Map<URI, Initiator> makeURIToInitiatorMap(Map<URI, List<Initiator>> netToNewInitiators, Map<Initiator, List<StoragePort>> argExistingAssignments) {
        Map<URI, Initiator> uriToInitiator = new HashMap<>();
        
        Set<URI> keySet = netToNewInitiators.keySet();
        for(URI netURI : keySet) {
            List<Initiator> list = netToNewInitiators.get(netURI);
            for(Initiator ini : list) {
                uriToInitiator.put(ini.getId(), ini);
            }
        }
        
        Set<Initiator> existingAssignmentKeyset = argExistingAssignments.keySet();
        for(Initiator exIni : existingAssignmentKeyset) {
            URI uri = exIni.getId();
            if(!uriToInitiator.containsKey(uri)) {
                uriToInitiator.put(uri, exIni);
            }
        }
        
        return uriToInitiator;
    }

    /**
     * Assigns storage ports to associated initiators
     * and adds it as an entry into assignments
     * 
     * @param uriToItsInitiator 
     * 
     * @param assignments
     */
    private void assignPortsToAssociatedInitiators(Map<URI, Initiator> uriToItsInitiator,
                                                   Map<Initiator, List<StoragePort>> assignments) {
        
        Map<Initiator, List<StoragePort>> assignmentsForAssociatedPorts = new HashMap<>();
        Set<Initiator> iniKeySet = assignments.keySet();
        for(Initiator initiator : iniKeySet) {
            URI associatedIniUri = initiator.getAssociatedInitiator();
            
            if(!NullColumnValueGetter.isNullURI(associatedIniUri)) {
                
              //Get the associated initiator object
              Initiator associatedInitiator = uriToItsInitiator.get(associatedIniUri);
              
              //Get the storage ports assigned to its associated initiator
              List<StoragePort> assignedStoragePorts = assignments.get(initiator);
              
              //Assign these same ports to self
              if(null!=associatedInitiator && !assignments.containsKey(associatedInitiator)) {
                  assignmentsForAssociatedPorts.put(associatedInitiator, assignedStoragePorts);
                  _log.info(String.format("Associated Initiator %s is assigned with storage ports %s",
                            associatedIniUri.toString(), assignedStoragePorts.toString()));
              }              
              
            }            
        }
        
        if(!assignmentsForAssociatedPorts.isEmpty()) {
            assignments.putAll(assignmentsForAssociatedPorts);
        }
        
    }

    /**
     * Removes associated initiators from the initiator list under consideration
     * and from the existing assignments.
     * 
     * @param associatedPairs
     * @param netToNewInitiators
     * @param argExistingAssignments
     */
    private void removeAssociatedInitiators(Map<Initiator, URI> associatedPairs,
                                            Map<URI, List<Initiator>> netToNewInitiators,
                                            Map<Initiator, List<StoragePort>> argExistingAssignments) {
        
        Set<Initiator> associatedIniKeyset = associatedPairs.keySet();
        for(Initiator ini : associatedIniKeyset) {
            URI uri = associatedPairs.get(ini);
            
            //update the existing assignments
            Iterator<Map.Entry<Initiator, List<StoragePort>>> it = argExistingAssignments.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<Initiator, List<StoragePort>> entry = it.next();
                Initiator key = entry.getKey();
                if(key.getId().equals(uri) && argExistingAssignments.containsKey(ini)) {
                    it.remove();
                }
            }
            
            //update netToNewInitiators
            Iterator<Map.Entry<URI, List<Initiator>>> it2 = netToNewInitiators.entrySet().iterator();
            while(it2.hasNext()) {
                Map.Entry<URI, List<Initiator>> entry2 = it2.next();
                List<Initiator> value2 = entry2.getValue();
                
                Iterator<Initiator> listItr = value2.iterator();
                for(; listItr.hasNext() ;) {
                    Initiator lisIni = listItr.next();
                    if(lisIni.getId().equals(uri) && value2.contains(ini)) {
                        listItr.remove();
                    }
                }
            }
        }
        
        
    }

    /**
     * Creates the map of initiator Vs its associated initiator URI
     * This would be just one side association, not the full
     * For e.g : If there are initiators [ Init1-> assocInit1, Init2->assocInit2]
     * then the resulting map contains [Init1:assocInit1, Init2:assocInit2]
     * 
     * @param netToNewInitiators
     * @return
     */
    private Map<Initiator, URI> makeInitiatorAssociatedInitiatorPairs(Map<URI, List<Initiator>> netToNewInitiators) {
        Map<Initiator, URI> initiatorPairs = new HashMap<>();
        Set<URI> keyset = netToNewInitiators.keySet();
        for(URI netURI : keyset) {
            List<Initiator> initiators = netToNewInitiators.get(netURI);
            for(Initiator initiator : initiators) {
                URI associatedInitiator = initiator.getAssociatedInitiator();
                if(!NullColumnValueGetter.isNullURI(associatedInitiator)) {
                    
                    if(!initiatorPairs.containsValue(initiator.getId())) {
                        initiatorPairs.put(initiator, associatedInitiator);
                    }
                    
                }
            }
        }
        
        return initiatorPairs;
    }

    /**
     * Iterates over the Networks, picking the first Initiator
     *
     * @param assignments
     * @param netToNewInitiators
     * @param portUseCounts
     * @param netToAllocatedPorts
     * @param initiatorToSwitchName
     * @param portAddressToSwitchName
     * @param switchToStoragePortsByNet
     * @param initiatorToNetworkLiteMap
     * @param pathParams
     * @param useAffinity
     * @param useNonAffinity
     */
    private void iterateAssignmentPasses(
            Map<Initiator, List<StoragePort>> assignments, Map<URI, List<Initiator>> netToNewInitiators,
            Map<StoragePort, Integer> portUseCounts, Map<URI, List<StoragePort>> netToAllocatedPorts,
            Map<Initiator, String> initiatorToSwitchName, Map<String, String> portAddressToSwitchName,
            Map<URI, Map<String, List<StoragePort>>> switchToStoragePortsByNet,
            Map<Initiator, NetworkLite> initiatorToNetworkLiteMap,
            ExportPathParams pathParams, boolean useAffinity, boolean useNonAffinity) {

        // Copy the map of network to initiators to be provisioned as this is destroyed
        Map<URI, List<Initiator>> netToInitiatorsToProvision = new HashMap<URI, List<Initiator>>();
        for (Map.Entry<URI, List<Initiator>> entry : netToNewInitiators.entrySet()) {
            // N.B. We must copy the initiator list so as not to affect caller's data
            netToInitiatorsToProvision.put(entry.getKey(), new ArrayList<Initiator>(entry.getValue()));
        }

        boolean hadInitiators = false;
        // Loop while progress is being made
        do {
            hadInitiators = false;
            // Loop over networks, adding equal numbers of ports per network
            for (Map.Entry<URI, List<Initiator>> entry : netToInitiatorsToProvision.entrySet()) {
                if (null == entry.getValue() || entry.getValue().isEmpty()) {
                    _log.info(String.format("No more initiators to provision net %s", entry.getKey()));
                    continue;
                }
                hadInitiators = true;
                int currentStoragePaths = portUseCounts.size();

                // Work on the first initiator
                Initiator initiator = entry.getValue().get(0);

                
                /*// give a try to assigning port form its associated initiator from assignments
                // not considering for path as it associate is already considered.
                boolean assigned = assignSamePortsFromAssociateInitiator(initiator, assignments);
                if (assigned) {
                    // Remove this initiator from further provisioning consideration
                    // and do not add this initiator for path consideration.
                    entry.getValue().remove(initiator);
                    continue;
                }*/
                
                // Determine the ports we can use, based on using affinity, non-affinity, or both.
                List<StoragePort> allocatedPorts = new ArrayList<StoragePort>();
                if (useAffinity) {
                    allocatedPorts.addAll(getPortsWithSwitchAffinity(entry.getKey(), initiator,
                            initiatorToSwitchName.get(initiator), switchToStoragePortsByNet));
                    // If there are no ports, we cannot provision this initiator, so look at next Network
                    if (allocatedPorts.isEmpty()) {
                        entry.getValue().remove(initiator);
                        continue;
                    }
                }
                if (useNonAffinity) {
                    List<StoragePort> nonAffinityPorts = new ArrayList<StoragePort>();
                    if (netToAllocatedPorts != null && netToAllocatedPorts.get(entry.getKey()) != null) {
                        nonAffinityPorts.addAll(netToAllocatedPorts.get(entry.getKey()));
                    }

                    // Remove duplicates
                    nonAffinityPorts.removeAll(allocatedPorts);
                    allocatedPorts.addAll(nonAffinityPorts);
                }

                // If there are no ports, we cannot provision this initiator, so look at next Network
                if (allocatedPorts.isEmpty()) {
                    entry.getValue().remove(initiator);
                    continue;
                }

                // Determine if ports have already been assigned (perhaps even partially but need additional ports.)
                int alreadyAssigned = 0;
                List<StoragePort> assignedPorts = assignments.get(initiator);
                if (assignedPorts != null) {
                    alreadyAssigned = assignedPorts.size();
                    if (alreadyAssigned >= pathParams.getPathsPerInitiator()) {
                        // Sufficient ports have already been assigned.
                        entry.getValue().remove(initiator);
                        // This counts as we added something because we processed a previous mapping
                        continue;
                    } else {

                        _log.info(String.format("Retaining partial assignments Initiator %s (%s):",
                                initiator.getInitiatorPort(), initiator.getHostName()));
                        for (StoragePort port : assignedPorts) {
                            _log.info(String.format("   Port %s (%s/%s) network %s assigned to initiator %s/%s (%s)\n",
                                    port.getPortName(), port.getPortNetworkId(),

                                    getPortSwitchName(port, portAddressToSwitchName), entry.getKey(),
                                    initiator.getInitiatorPort(), getInitiatorSwitchName(initiator, initiatorToSwitchName),
                                    initiator.getHostName()));
                            addPortUse(portUseCounts, port);
                        }
                    }
                }

                if ((currentStoragePaths + pathParams.getPathsPerInitiator() - alreadyAssigned)
                <= (pathParams.getMaxPaths() / pathParams.getMaxInitiatorsPerPort())) {

                    List<StoragePort> availPorts = getAvailablePorts(initiator, initiatorToNetworkLiteMap,
                            allocatedPorts, portUseCounts, pathParams.getPathsPerInitiator() - alreadyAssigned, 0);

                    if (availPorts != null) {
                        assignPorts(assignments, entry.getKey(), initiator, availPorts, portUseCounts, portAddressToSwitchName,
                                initiatorToSwitchName);
                    } else {
                        _log.info(String.format("Insufficient ports to provision initiator %s/%s (%s)",
                                initiator.getInitiatorPort(), getInitiatorSwitchName(initiator, initiatorToSwitchName),
                                initiator.getHostName()));
                    }
                }
                entry.getValue().remove(initiator);
            }
        } while (hadInitiators);
    }

    /**
     * Given a network URI, initiator, and switchName of the initiator,
     * returns switch ports (if any) with the same switch affinity.
     *
     * @param netURI
     * @param initiator
     * @param switchName
     * @param switchToStoragePortsByNet
     * @return - list of StoragePorts with same switch affinity
     */
    private List<StoragePort> getPortsWithSwitchAffinity(URI netURI, Initiator initiator, String switchName,
            Map<URI, Map<String, List<StoragePort>>> switchToStoragePortsByNet) {
        List<StoragePort> portsWithAffinity = new ArrayList<StoragePort>();
        // Find the initiators and storage ports are connected to the same switch.
        Map<String, List<StoragePort>> switchPorts = switchToStoragePortsByNet.get(netURI);
        if (switchPorts != null && switchPorts.containsKey(switchName)) {
            portsWithAffinity.addAll(switchPorts.get(switchName));
        }
        return portsWithAffinity;
    }

    /**
     * Adds a use count to a port, which indicates one initiator is using the port
     * This is public static because the StoragePortsAssignerTest uses it.
     *
     * @param portUseCounts -- Map of StoragePort to use counts
     * @param port -- Port being used
     */
    public static void addPortUse(Map<StoragePort, Integer> portUseCounts, StoragePort port) {
        if (!portUseCounts.containsKey(port)) {
            portUseCounts.put(port, 1);
        } else {
            Integer newCount = portUseCounts.get(port) + 1;
            portUseCounts.put(port, newCount);
        }
    }

    /**
     * Returns true if the port is being used
     *
     * @param portUseCounts -- Map of Storage Port to use counts
     * @param port -- Port we are inquiring about
     * @return
     */
    private boolean isPortUsed(Map<StoragePort, Integer> portUseCounts, StoragePort port) {
        return portUseCounts.containsKey(port);
    }

    /**
     * Gets available ports with the lowest use count (must be <= maxUseCount).
     *
     * @param initiator -- the Initiator the ports are for
     * @param allocatedPorts -- List of allocated ports from which we can choose
     * @param portUseCounts -- Map of StoragePort to use counts
     * @param numberOfPorts -- int number of ports required (returns all or null)
     * @param maxUseCount -- The maximum use count we want.
     *            If zero, we want ports that are not previously used by this host (useCount == 0).
     *            If >zero, we can accept any ports for this host upto maxUseCount (1 <= useCount <= maxUseCount)
     * @return List of the ports to be used with number of ports requested, or
     *         null if the required number of ports could not be found
     */
    private List<StoragePort> getAvailablePorts(Initiator initiator,
            Map<Initiator, NetworkLite> initiatorToNetworkLiteMap, List<StoragePort> allocatedPorts,
            Map<StoragePort, Integer> portUseCounts, int numberOfPorts, int maxUseCount) {
        List<StoragePort> availPorts = new ArrayList<StoragePort>();
        if (allocatedPorts == null || allocatedPorts.isEmpty() || numberOfPorts < 1) {
            return null;
        }
        // If maxUseCount > 0, we are trying to reuse ports for multiple initiators,
        // so do not return any ports that have not already been used by this host.
        int minUseCount = (maxUseCount > 0) ? 1 : 0;
        for (int useCount = minUseCount; useCount <= maxUseCount; useCount++) {
            for (StoragePort port : allocatedPorts) {
                if (initiatorToNetworkLiteMap != null
                        && !isPortAssignableToInitiator(initiatorToNetworkLiteMap.get(initiator), initiator, port)) {
                    // skip this port if not assignable (because of prezoning)
                    continue;
                }
                if (!portUseCounts.containsKey(port)) {
                    if (useCount == 0) {
                        availPorts.add(port);
                    }
                } else if (portUseCounts.get(port) == useCount) {
                    availPorts.add(port);
                }
                if (availPorts.size() == numberOfPorts) {
                    return availPorts;
                }
            }
        }
        // not enough ports available
        return null;
    }

    /**
     * Assigns the ports, updates the port use counts
     *
     * @param assignments Map of Initiator to List<StoragePort> for new assignments
     * @param netURI - Network these ports are in
     * @param initiator -- The initiators ports are being assigned for
     * @param assignedPorts -- The list of storage ports being assigned
     * @param portUseCounts -- Map of ports to use counts
     * @param portAddressToSwitchMap -- map of port Address to switch name
     * @param initiatorToSwitchMap - map of initiator to switch map
     */
    private void assignPorts(Map<Initiator, List<StoragePort>> assignments,
            URI netURI,
            Initiator initiator, List<StoragePort> assignedPorts,
            Map<StoragePort, Integer> portUseCounts, Map<String, String> portAddressToSwitchMap,
            Map<Initiator, String> initiatorToSwitchMap) {
        for (StoragePort port : assignedPorts) {
            _log.info(String.format("Port %s (%s/%s) network %s assigned to initiator %s/%s (%s)\n",
                    port.getPortName(), port.getPortNetworkId(),
                    getPortSwitchName(port, portAddressToSwitchMap), netURI,
                    initiator.getInitiatorPort(), getInitiatorSwitchName(initiator, initiatorToSwitchMap),
                    initiator.getHostName()));
            addPortUse(portUseCounts, port);
        }
        if (assignments.get(initiator) != null) {
            assignments.get(initiator).addAll(assignedPorts);
        } else {
            assignments.put(initiator, assignedPorts);
        }
    }

    /**
     * Will generate a default empty List<Initiator> if the passed one is null.
     * Otherwise returns original list.
     * Used to reduce cyclomatic complexity.
     *
     * @param initiatorList - null or a list of initiators
     * @return a non null list of initiators, possibly empty
     */
    private List<Initiator> nonNullInitiatorList(List<Initiator> initiatorList) {
        if (initiatorList != null) {
            return initiatorList;
        }
        return new ArrayList<Initiator>();
    }

    /**
     * Will generate an empty Map<Initiator, List<StoragePort> if the passed assignmentMap is null.
     * Otherwise returns the original map. Used to reduce cyclomatic complexity.
     *
     * @param assignmentMap -- Map(Initiator, List<StoragePort>> or null
     * @return non null Map, possibly empty
     */
    private Map<Initiator, List<StoragePort>> nonNullAssignmentMap(Map<Initiator, List<StoragePort>> assignmentMap) {
        if (assignmentMap != null) {
            return assignmentMap;
        }
        return new HashMap<Initiator, List<StoragePort>>();
    }

    /**
     * Makes a map of StoragePort portNetworkId to switch name. Used for logging.
     *
     * @param switchToStoragePortsByNet Map of network to map of switch name to storage ports
     * @return Map of port network id to switch name
     */
    private Map<String, String> makePortAddressToSwitchMap(Map<URI, Map<String, List<StoragePort>>> switchToStoragePortsByNet) {
        Map<String, String> result = new HashMap<String, String>();
        if (switchToStoragePortsByNet == null) {
            return result;
        }
        for (Map<String, List<StoragePort>> switchToPorts : switchToStoragePortsByNet.values()) {
            for (Map.Entry<String, List<StoragePort>> entry : switchToPorts.entrySet()) {
                for (StoragePort port : entry.getValue()) {
                    result.put(port.getPortNetworkId(), entry.getKey());
                    _log.info(String.format("%s WWN %s SWITCH %s NET %s", 
                            port.getPortName(), port.getPortNetworkId(), entry.getKey(), port.getNetwork()));
                }
            }
        }
        return result;
    }

    /**
     * Given a portAddressToSwitchMap and a port, returns the switch name
     *
     * @param port -- StoragePort
     * @param portAddressToSwitchMap -- map of Port NetworkId to Switch Name
     * @return -- Switch name string
     */
    private String getPortSwitchName(StoragePort port, Map<String, String> portAddressToSwitchMap) {
        if (portAddressToSwitchMap == null || !portAddressToSwitchMap.containsKey(port.getPortNetworkId())) {
            return "sw?";
        }
        return portAddressToSwitchMap.get(port.getPortNetworkId());
    }

    /**
     * Returns a map of Initiator to Switch Name
     *
     * @param switchToInitiatorsByNetwork -- Map of Network to Map of SwitchName to List of Initiators
     * @return == Map of Initiator to Switch Name
     */
    private Map<Initiator, String> makeInitiatorToSwitchMap(Map<URI, Map<String, List<Initiator>>> switchToInitiatorsByNetwork) {
        Map<Initiator, String> initiatorToSwitchMap = new HashMap<Initiator, String>();
        if (switchToInitiatorsByNetwork == null) {
            return initiatorToSwitchMap;
        }

        for (Map<String, List<Initiator>> switchToInitiators: switchToInitiatorsByNetwork.values()) {
            for (Map.Entry<String, List<Initiator>> entry : switchToInitiators.entrySet()) {
                for (Initiator initiator : entry.getValue()) {
                    initiatorToSwitchMap.put(initiator,  entry.getKey());

                }
            }
        }
        return initiatorToSwitchMap;
    }

    private String getInitiatorSwitchName(Initiator initiator, Map<Initiator, String> initiatorToSwitchMap) {
        if (initiatorToSwitchMap == null || !initiatorToSwitchMap.containsKey(initiator)) {
            return "sw?";
        }
        return initiatorToSwitchMap.get(initiator);

    }
    
    /**
     * Gets the associated initiator from the map of assignments.
     * 
     * @param initiator
     * @param assignments the map of initiator - storage ports
     * @return the associated initiator. Null if associated initiator is not in the assignment map.
     */
    private Initiator getAssociatedInitiator(Initiator initiator, Map<Initiator, List<StoragePort>> assignments) {

        Set<Initiator> initiators = assignments.keySet();

        for (Iterator<Initiator> iterator = initiators.iterator(); iterator.hasNext();) {

            Initiator initiatorInAssignments = iterator.next();
            URI associatedInitiator2 = initiatorInAssignments.getAssociatedInitiator();

            if (!NullColumnValueGetter.isNullURI(associatedInitiator2) &&
                    initiator.getId().equals(associatedInitiator2)) {
                return initiatorInAssignments;
            }
        }
        return null;

    }

    /**
     * this function count actual initiator.Mean it takes list of initiator and ignore the inititoar if it has
     * Already consider it paired
     * 
     * @param list
     * @return
     */
    private int getInitiatorCount(List<Initiator> list) {
        List<URI> processInitiator = new ArrayList<URI>();
        int count = 0;
        for (Initiator currentInitiator : list) {
            URI pair = currentInitiator.getAssociatedInitiator();
            if (NullColumnValueGetter.isNullURI(pair)) {
                count++;
            } else if (!processInitiator.contains(pair)) {
                processInitiator.add(currentInitiator.getId());
                count++;
            }
        }

        return count;
    }

    /**
     * Assigns the storage ports to the initiator if associated initiator is in the map of assignments.
     * 
     * @param initiator initiator to which storage ports need to be assigned.
     * @param assignments Map between initiator to Storage port assignment
     * @return true is assigned, false otherwise
     */
    /*private boolean assignSamePortsFromAssociateInitiator(Initiator initiator,
            Map<Initiator, List<StoragePort>> assignments) {

        boolean assigned = false;
        Initiator associatedInitiator = getAssociatedInitiator(initiator, assignments);

        if (associatedInitiator != null) {
            List<StoragePort> sports = assignments.get(associatedInitiator);
            if (sports != null && !sports.isEmpty()) {
                if (assignments.get(initiator) != null) {
                    assignments.get(initiator).addAll(sports);
                } else {
                    assignments.put(initiator, sports);
                }
                assigned = true;
            }

        }
        return assigned;

    }*/
}
