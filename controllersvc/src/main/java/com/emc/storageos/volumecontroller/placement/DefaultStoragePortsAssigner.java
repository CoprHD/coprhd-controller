/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.placement;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
            Map<URI, Set<Initiator>> existingInitiatorsMap) // existing initiators grouped by Network
            throws PlacementException {
        if (existingPortsMap == null) {
            existingPortsMap = new HashMap<URI, Set<StoragePort>>();
        }
        if (existingInitiatorsMap == null) {
            existingInitiatorsMap = new HashMap<URI, Set<Initiator>>();
        }

        URI[] networkURIs = new URI[0];
        networkURIs = net2InitiatorsMap.keySet().toArray(networkURIs);
        Integer allocated = 0;
        Integer[] allocatedReference = new Integer[] { allocated };

        // Counts per network of ports to be newly allocated:
        // start with the existing number of ports allocated in each network
        Map<URI, Integer> net2NumPortsMap = makeNetwork2NumExistingPortsMap(
                existingPortsMap, allocatedReference);
        allocated = allocatedReference[0];

        for (Map.Entry<URI, Integer> entry : net2NumPortsMap.entrySet()) {
            _log.info(String.format("Existing network %s allocated %d", entry.getKey().toString(), entry.getValue()));
        }

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

        // Get the maximum number of initiators in a single host for each given Network.
        // This is the combination of the existing initiators and the new initiators.
        Map<URI, Integer> net2MaxHostInitiators = makeNetwork2MaxHostInitiators(
                allInitiatorsMap);

        // Strategy: we iterate over the networks, adding pathsPerInitiator ports at a time
        // to each network that has fewer paths than maxHostInitiators * pathsPerInitiator.
        int maxPaths = pathParams.getMaxPaths();
        int pathsPerInitiator = pathParams.getPathsPerInitiator();
        boolean addedThisPass;
        do {
            addedThisPass = false;
            for (int index = 0; index < networkURIs.length; index++) {
                URI networkURI = networkURIs[index];
                int maxHostInitiators = net2MaxHostInitiators.get(networkURI);
                if (net2NumPortsMap.get(networkURI) == null) {
                    net2NumPortsMap.put(networkURI, new Integer(0));
                }
                // If we currently have fewer paths in our network
                // than maxHostInitiators * pathsPerInitiator,
                // add a path if allocated < maxPaths.
                Integer currentPorts = net2NumPortsMap.get(networkURI);
                if (currentPorts < (maxHostInitiators * pathsPerInitiator)) {
                    if (allocated <= (maxPaths - pathsPerInitiator)) {
                        net2NumPortsMap.put(networkURI, currentPorts + pathsPerInitiator);
                        addedThisPass = true;
                        allocated += pathsPerInitiator;
                    }
                }
            }
        } while (addedThisPass && allocated <= (maxPaths - pathsPerInitiator));

        // Calculate any previously allocated ports not currently accounted for.
        int previouslyAllocated = 0;
        for (URI netURI : existingPortsMap.keySet()) {
            if (!net2NumPortsMap.containsKey(netURI)) {
                // not allocating for this Network; add previous ports as paths
                previouslyAllocated += existingPortsMap.get(netURI).size();
            }
        }

        // Check that we are above minPaths
        if ((allocated + previouslyAllocated) < pathParams.getMinPaths()) {
            int initiatorCount = 0;
            for (Set<Initiator> initSet : allInitiatorsMap.values()) {
                initiatorCount += initSet.size();
            }
            _log.info(String.format("Could not allocate min_path number of ports: ports needed %d "
                    + "total initiators %d paths_per_initiator %d min_paths %d max_paths %d",
                    allocated, initiatorCount, pathParams.getPathsPerInitiator(),
                    pathParams.getMinPaths(), pathParams.getMaxPaths()));
            throw PlacementException.exceptions.cannotAllocateMinPaths(allocated, initiatorCount,
                    pathParams.getPathsPerInitiator(), pathParams.getMinPaths(), pathParams.getMaxPaths());
        }

        return net2NumPortsMap;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.placement.StoragePortsAssigner#assign(java.util.Map, java.util.List, java.util.List)
     */
    @Override
    public void assign(Map<Initiator, List<StoragePort>> assignments,
            List<Initiator> initiators, List<StoragePort> storagePorts,
            ExportPathParams pathParams,
            Map<Initiator, List<StoragePort>> existingAssignments, NetworkLite initiatorNetwork) {

        // Make a map of hosts to the initiators they contain.
        Map<URI, List<Initiator>> hostInitiatorsMap = makeHostInitiatorsMap(initiators);

        // If there have already been assignments to this ExportMask,
        // add more assignments based on the new Initiators.
        if (existingAssignments != null && !existingAssignments.isEmpty()) {
            addAssignments(assignments, storagePorts,
                    pathParams.getPathsPerInitiator(),
                    hostInitiatorsMap, existingAssignments, initiatorNetwork);
            return;
        }

        // Otherwise all the assignments are for new hosts so add them here.
        _log.info(String.format("Assigning ports to new Hosts: %s",
                hostInitiatorsMap.keySet().toString()));
        assignPortsToHosts(assignments, storagePorts,
                pathParams.getPathsPerInitiator(), hostInitiatorsMap, initiatorNetwork);
    }

    /**
     * Assign the ports to a set of new hosts in the network.
     * 
     * @param assignments OUTPUT Map of list of StoragePorts representing the assignments
     * @param storagePorts INPUT the ports that can be used for assignment
     * @param pathsPerInitiator INPUT the desired number of paths per initiator
     * @param hostInitiatorsMap INPUT a map of Host URI to the Initiators in that host
     * @param initiatorNetwork INPUT the initiators network
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
                                isPortAssignableToInitiator(initiatorNetwork, initiator, port)) {
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
     * Handle the various cases for adding additional initiators to one Network.
     * These may be in additional hosts, or adding initiators to existing hosts.
     * Outline of algorithm:
     * 1. Determine the existing hosts from the existingZoningMap.
     * 2. For each host in the hostsInitiatorsMap, determine if it is a new host,
     * or an existing host.
     * 3. For each new host, use all the port assignments.
     * 4. For adding new initiators to a host, use only new port assignments, if any.
     * 
     * @param assignments OUTPUT map of Initiator to List<StoragePort> assignments
     * @param storagePorts INPUT storage ports that have been allocated newly allocated or previously existed
     * @param pathsPerInitiator integer value of paths desired per initiator
     * @param hostInitiatorsMap Map of host URI to the new set of initiators
     * @param existingAssignments Map of previously zoned Initiators to StoragePorts
     * @param initiatorNetwork The network of the initiator
     * @param initiators INPUT new initiators to be added into the mix
     */
    public void addAssignments(Map<Initiator, List<StoragePort>> assignments,
            List<StoragePort> storagePorts,
            int pathsPerInitiator,
            Map<URI, List<Initiator>> hostInitiatorsMap,
            Map<Initiator, List<StoragePort>> existingAssignments, NetworkLite initiatorNetwork) {

        // Arrange the storagePorts based on the existing assignments.
        // Start with initiators that have multiple port assignments first.
        // The idea is that they should be kept together for proper redundancy.
        List<StoragePort> newPorts = new ArrayList<StoragePort>();
        List<StoragePort> sortedPorts = sortPorts(storagePorts, existingAssignments, newPorts, initiatorNetwork);

        // Make set of previous hosts URIs.
        Set<URI> previousHosts = new HashSet<URI>();
        for (Initiator initiator : existingAssignments.keySet()) {
            previousHosts.add(initiator.getHost());
        }

        // Collect the new hosts, and assign their initiators.
        Map<URI, List<Initiator>> newHostInitiatorsMap = new HashMap<URI, List<Initiator>>();
        for (URI hostURI : hostInitiatorsMap.keySet()) {
            if (!previousHosts.contains(hostURI)) {
                // It's a new host!
                _log.info(String.format("Adding new host: %s", hostURI));
                newHostInitiatorsMap.put(hostURI, hostInitiatorsMap.get(hostURI));
            } else {
                // Determine ports that can be used for this host. They are any ports
                // not currently used by this host, plus any new ports.
                List<StoragePort> unusedPorts = new ArrayList<StoragePort>();
                // Get all the ports
                unusedPorts.addAll(sortedPorts);
                // Remove any used ones as given by the existing assignments.
                for (Initiator initiator : existingAssignments.keySet()) {
                    if (initiator.getHost().equals(hostURI)) {
                        for (StoragePort port : existingAssignments.get(initiator)) {
                            unusedPorts.remove(port);
                        }
                    }
                }
                // If we have unused or new ports, assign them
                if (!unusedPorts.isEmpty()) {
                    // Assign ports for new initiators in existing host.
                    _log.info(String.format("Adding new initiators to existing host: %s", hostURI));
                    Map<URI, List<Initiator>> existingHostInitiatorsMap = new HashMap<URI, List<Initiator>>();
                    existingHostInitiatorsMap.put(hostURI, hostInitiatorsMap.get(hostURI));
                    assignPortsToHosts(assignments, unusedPorts, pathsPerInitiator, existingHostInitiatorsMap, initiatorNetwork);
                } else {
                    _log.info(String.format(
                            "No unused or new ports available for new initiators in host: %s", hostURI));
                }
            }
        }

        // Assign all the ports for new hosts at one time.
        if (!newHostInitiatorsMap.isEmpty()) {
            assignPortsToHosts(assignments, sortedPorts, pathsPerInitiator, newHostInitiatorsMap, initiatorNetwork);
        }
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
     * @param initiatorNetwork the network of the initiator
     * @return
     */
    private List<StoragePort> sortPorts(List<StoragePort> storagePorts,
            Map<Initiator, List<StoragePort>> existingAssignments,
            List<StoragePort> newPorts, NetworkLite initiatorNetwork) {
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
     * Makes a map of network URI to the number of existing (already allocated ports).
     * 
     * @param existingPortsMap - Map of network URI to the storage ports that have
     *            already been allocated.
     * @param allocated - output parameter in allocated[0] of number of ports allocated so far
     * @return Map<URI, Integer> will have an entry for all networks
     */
    protected Map<URI, Integer> makeNetwork2NumExistingPortsMap(
            Map<URI, Set<StoragePort>> existingPortsMap, Integer[] allocated) {
        // Counts per network of ports previously allocated:
        Map<URI, Integer> net2NumExistingPortsMap = new HashMap<URI, Integer>();
        // Account for pre-existing allocated ports.
        // We keep counts of the already allocated ports per network in net2NumExistingPortsMap.
        if (existingPortsMap != null) {
            for (URI initiatorNetworkURI : existingPortsMap.keySet()) {
                Set<StoragePort> existingPorts = existingPortsMap.get(initiatorNetworkURI);
                if (existingPorts != null) {
                    net2NumExistingPortsMap.put(initiatorNetworkURI, existingPorts.size());
                    allocated[0] += existingPorts.size();
                } else {
                    net2NumExistingPortsMap.put(initiatorNetworkURI, 0);
                }
            }
        }
        return net2NumExistingPortsMap;
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
                int thisHost = hostInitiatorsMap.get(host).size();
                max = (thisHost > max) ? thisHost : max;
            }
            net2MaxHostInitiators.put(net, max);
            _log.info(String.format("Network %s max initiators per host %d", net.toString(), max));
        }
        return net2MaxHostInitiators;
    }

    @Override
    public boolean isPortAssignableToInitiator(NetworkLite initiatorNetwork, Initiator initiator, StoragePort port) {
        return true;
    }
}
