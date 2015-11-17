/*
 * Copyright (c) 2008-2012 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.placement;

import static com.google.common.collect.Collections2.transform;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.networkcontroller.impl.NetworkScheduler;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;

/**
 * StorageScheduler service for block and file storage. StorageScheduler is done based on desired
 * class-of-service parameters for the provisioned storage.
 */
public class BlockStorageScheduler {
    protected static final Logger _log = LoggerFactory.getLogger(BlockStorageScheduler.class);
    private DbClient _dbClient;
    private PortMetricsProcessor _portMetricsProcessor;
    private NetworkScheduler _networkScheduler;

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        if (_portMetricsProcessor == null) {
            _portMetricsProcessor = portMetricsProcessor;
        }
    }

    public void setNetworkScheduler(NetworkScheduler networkScheduler) {
        if (_networkScheduler == null) {
            _networkScheduler = networkScheduler;
        }
    }

    /**
     * Determine if an Initiator has connectivity to a StorageSystem.
     * This check requires only one port be connectable.
     * 
     * @param storage StorageSystem
     * @param varray VirtualArray
     * @param initiator Initiator
     * @return the ports determined to be usable
     * @throws PlacementException
     */
    // DEAD CODE? TLW
//    public List<URI> getAllocatableStorageSystemTargetPorts(StorageSystem storage, URI varray, Initiator initiator) {
//        List<URI> sports = new ArrayList<URI>();
//        NetworkLite network = getInitiatorNetwork(initiator, _dbClient);
//        if (network == null) {
//            return sports;
//        }
//        Map<URI, NetworkLite> networkMap = new HashMap<URI, NetworkLite>();
//        networkMap.put(network.getId(), network);
//        StoragePortsAllocator allocator = new StoragePortsAllocator();
//        Set<StoragePort> previouslyAllocatedPorts = new HashSet<StoragePort>();
//        List<URI> orderedNetworks = new ArrayList<URI>();
//        Map<URI, Map<StoragePort, Long>> portUsageMap =
//                computeStoragePortUsageMap(storage.getId(), networkMap, varray, orderedNetworks);
//        if (portUsageMap.get(network.getId()).isEmpty()) {
//            throw PlacementException.exceptions.cannotAllocateRequestedPorts(
//                    network.getLabel(), storage.getNativeGuid(), 1, 0, 0);
//        }
//        sports.addAll(getPortURIs(allocatePortsFromNetwork(storage.getId(), network, varray, 1,
//                portUsageMap.get(network.getId()), allocator, previouslyAllocatedPorts, false)));
//        return sports;
//    }

    /**
     * Invoke placement to select storage ports for export, and then
     * to assign specific storage ports to specific initiators.
     * 
     * Note: This method returns only new assignments but validates minPaths
     * for all assignments(existing and new).
     * 
     * @param storage
     * @param initiators - The new initiators to be provisioned.
     * @param pathParams - the Export Path parameters (maxPaths, pathsPerInitiator)
     * @param existingZoningMap the zones that already exist on the mask plus any new mapping added
     *            by assigning pre-zoned ports.
     * @param volumeURIs the URIs of the volumes in the mask
     * @param the export virtual array
     * @return Map<URI, List<URI> map of Initiator URIs to list of StoragePort URIs to be used by Initiator
     * @throws DeviceControllerException if there is an unexpected error
     * @throws PlacementException if we were unable to meet the placement constraints (such as minPaths.)
     */
    public Map<URI, List<URI>> assignStoragePorts(StorageSystem storage, URI virtualArray,
            List<Initiator> initiators,
            ExportPathParams pathParams,
            StringSetMap existingZoningMap,
            Collection<URI> volumeURIs) throws DeviceControllerException {
        Map<URI, List<URI>> assignmentMap = null;
        boolean backend = ExportMaskUtils.areBackendInitiators(initiators);
        if (!allocateFromPrezonedPortsOnly(virtualArray, storage.getSystemType(), backend)) {
            try {
                assignmentMap = internalAssignStoragePorts(storage, virtualArray,
                        initiators, volumeURIs, pathParams, existingZoningMap);
            } catch (PlacementException e) {
                _log.error("Unable to assign storage Ports", e);
                throw DeviceControllerException.exceptions.exceptionAssigningStoragePorts(e.getMessage(), e);
            } catch (Exception e) {
                _log.error("Unable to assign Storage Ports", e);
                throw DeviceControllerException.exceptions.unexpectedExceptionAssigningPorts(e);
            }
        } else {
            // assignment should be made solely based on pre-zoned ports
            _log.info("Manual zoning is specified for this virtual array and the system configuration is to use "
                    + "existing zones. Assign port storage will not add any additional ports.");
            assignmentMap = new HashMap<URI, List<URI>>();
        }
        return assignmentMap;
    }

    /**
     * Allocates and assigns StoragePorts.
     * 
     * @param system - The StorageSystem the ports will be assigned from.
     * @param varray - The VirtualArray (ex. Neighborhood) the initiators should be found in.
     * @param newInitiators - The new initiators to be provisioned.
     * @param volumeURIs - list of volumes
     * @param pathParams - Export path parameters (maxPaths, pathsPerInitiator)
     * @param existingZoningMap - A map of initiators to a set of previously allocated port URI strings.
     * @return Map<URI, List<URI>> Initiator URI to list of Target StoragePort URIs
     */
    private Map<URI, List<URI>> internalAssignStoragePorts(StorageSystem system,
            URI varray, List<Initiator> newInitiators,
            Collection<URI> volumeURIs,
            ExportPathParams pathParams,
            StringSetMap existingZoningMap) {

        // Make reasonable defaults for the path parameters.
        checkPathParams(pathParams, system);

        _log.info(String.format("Assigning Ports for Array %s params %s Varray %s",
                system.getNativeGuid(),
                pathParams.toString(), varray));

        // Get the existing assignments in object form.
        Map<Initiator, List<StoragePort>> existingAssignments =
                generateInitiatorsToStoragePortsMap(existingZoningMap, varray);
        // Group the new initiators by their networks - filter out those not in a network or already in the existingZoningMap
        Map<NetworkLite, List<Initiator>> initiatorsByNetwork = getNewInitiatorsByNetwork(newInitiators, existingZoningMap, _dbClient);
        // Get the storage ports in the storage system that can be used in the initiators networks
        Map<NetworkLite, List<StoragePort>> portsByNetwork =
                selectStoragePortsInNetworks(system.getId(), initiatorsByNetwork.keySet(), varray, pathParams);
        // allocate ports balancing across networks and considering port metrics
        Map<NetworkLite, List<StoragePort>> allocatedPorts = allocatePorts(system,
                varray, initiatorsByNetwork, portsByNetwork, volumeURIs, pathParams, existingZoningMap);

        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(system.getSystemType());
        Map<Initiator, List<StoragePort>> assignments = new HashMap<Initiator, List<StoragePort>>();

        for (NetworkLite network : allocatedPorts.keySet()) {
            // Assign the Storage Ports.
            assigner.assign(assignments, initiatorsByNetwork.get(network),
                    allocatedPorts.get(network), pathParams,
                    existingAssignments, network);
        }

        // Validate that minPaths was met across all assignments (existing and new).
        validateMinPaths(system, pathParams, existingAssignments, assignments, newInitiators);
        return convertAssignmentsToURIs(assignments);
    }

    /**
     * This function performs checks to ensure the minimum path requirement is met for the export.
     * If this call is for adding additional paths, the validation is across existing and new assignments.
     * 
     * @param system the storage system of the export
     * @param pathParams the aggregate pathParam for all the export volumes
     * @param existingAssignments the existing initiator-port assignments. This can be empty for new assignments.
     * @param assignments the assignments made by this call to assign ports
     * @param existingInitiatorsMap the existing host-initiators map. This can be empty for new assignments.
     * @param newInitiators the list of initiators that received assignments
     * @throws PlacementException if minimum paths not met
     */
    private void validateMinPaths(StorageSystem system, ExportPathParams pathParams,
            Map<Initiator, List<StoragePort>> existingAssignments,
            Map<Initiator, List<StoragePort>> assignments,
            List<Initiator> newInitiators) {
        // Validate that minPaths was met across all assignments (existing and new).
        // Get a map of Network URIs to Initiators for the existing Initiators.
        Map<URI, Set<Initiator>> existingInitiatorsMap = generateNetworkToInitiatorsMap(existingAssignments, _dbClient);
        Map<Initiator, List<StoragePort>> allAssignments = new HashMap<Initiator, List<StoragePort>>();
        allAssignments.putAll(existingAssignments);
        allAssignments.putAll(assignments);
        // This min path check is done on allInitiators
        Collection<Initiator> allInitiators = new HashSet<Initiator>(newInitiators);
        for (Set<Initiator> existingInitiators : existingInitiatorsMap.values()) {
            allInitiators.addAll(existingInitiators);
        }
        Map<URI, List<Initiator>> hostInitiatorsMap = DefaultStoragePortsAssigner.makeHostInitiatorsMap(allInitiators);
        validateMinPaths(pathParams, hostInitiatorsMap, allAssignments);
        if (needToValidateHA(system)) {
            validateHACapabilities(pathParams, allAssignments);
        }
    }

    /**
     * Given a collection of storage ports on a storage system, a given list of initiators
     * belonging to a host or cluster, and given an export for which some port may already
     * be allocated ('add initiator' use case), apply the port selection algorithm to find
     * the ports that should be used by (or 'added to' for 'add initiator' use case).
     * 
     * @param system the storage system of the export
     * @param varray the export varray
     * @param initiatorsByNetwork the initiators to which the ports will be assigned,
     *            grouped by network
     * @param portsByNetwork the ports from which the allocation will be made. Note
     *            this function can be called to select ports from pre-zoned ports, in
     *            which can be a subset of all the available ports on the storage system.
     * @param volumeURIs all the export volumes, new and existing
     * @param pathParams the export path parameter which accounts for the paths
     *            requirement for all the volumes vpools.
     * @param existingZoningMap existing allocations, null is no allocations exist.
     * @return the selected ports to be used in the export.
     * 
     */
    public Map<NetworkLite, List<StoragePort>> allocatePorts(StorageSystem system, URI varray,
            Map<NetworkLite, List<Initiator>> initiatorsByNetwork,
            Map<NetworkLite, List<StoragePort>> portsByNetwork,
            Collection<URI> volumeURIs,
            ExportPathParams pathParams,
            StringSetMap existingZoningMap) {

        // Make reasonable defaults for the path parameters.
        checkPathParams(pathParams, system);

        _log.info(String.format("Assigning Ports for Array %s params %s Varray %s",
                system.getNativeGuid(),
                pathParams.toString(), varray));

        // Get the existing assignments in object form.
        Map<Initiator, List<StoragePort>> existingAssignments =
                generateInitiatorsToStoragePortsMap(existingZoningMap, varray);
        // Get a map of Network URIs to Initiators for the existing Initiators.
        Map<URI, Set<Initiator>> existingInitiatorsMap = generateNetworkToInitiatorsMap(existingAssignments, _dbClient);
        // Get a map of Network URIs to StoragePorts for the existing Storage Ports.
        Map<URI, Set<StoragePort>> existingPortsMap = generateNetworkToStoragePortsMap(existingAssignments, existingInitiatorsMap);

        // Make Net to Initiators Map and URI to Network map.
        Map<URI, List<Initiator>> net2InitiatorsMap = new HashMap<URI, List<Initiator>>();
        Map<URI, NetworkLite> networkMap = new HashMap<URI, NetworkLite>();
        for (NetworkLite network : initiatorsByNetwork.keySet()) {
            if (!networkMap.containsKey(network.getId())) {
                networkMap.put(network.getId(), network);
                net2InitiatorsMap.put(network.getId(), initiatorsByNetwork.get(network));
            }
        }

        // Filter Initiators by access - if a host has local access to the storage system
        // remove initiators that are routed to it - Should this be done when initiators are added or is this an override?
        filterRemoteInitiators(system, varray, net2InitiatorsMap, networkMap);

        // Compute the number of Ports needed for each Network
        StoragePortsAssigner assigner = StoragePortsAssignerFactory.getAssigner(system.getSystemType());
        Map<URI, Integer> net2PortsNeeded = assigner.getPortsNeededPerNetwork(net2InitiatorsMap,
                pathParams, existingPortsMap, existingInitiatorsMap);
        for (Map.Entry<URI, Integer> entry : net2PortsNeeded.entrySet()) {
            if (networkMap.get(entry.getKey()) != null) {
                _log.info(String.format("Network %s (%s) requested ports %d",
                        networkMap.get(entry.getKey()).getLabel(), entry.getKey().toString(), entry.getValue()));
            }
        }

        // For each Network, allocate the ports required, and then assign the ports.
        StoragePortsAllocator allocator = new StoragePortsAllocator();

        // In case this is an update of existing allocation, add all the previously
        // allocated ports into the context _alreadyAllocatedXXX fields.
        // This allows knowledge that an previous allocation in director A was made
        // so that in a Vpool upgrade we can allocate a different director
        // in a different network.
        for (URI netURI : existingPortsMap.keySet()) {
            NetworkLite network = networkMap.get(netURI);
            Set<StoragePort> existingPorts = existingPortsMap.get(netURI);
            allocator.addPortsToAlreadyAllocatedContext(_dbClient, network, existingPorts);
        }

        // Compute the StoragePort usage map. This also generates the ordering that
        // the allocation should be done in.
        List<URI> orderedNetworks = new ArrayList<URI>();
        Map<URI, Map<StoragePort, Long>> portUsageMap =
                computeStoragePortUsageMapForPorts(system.getId(),
                        networkMap, varray, orderedNetworks,
                        portsByNetwork);

        // Filter out the ports in the case of VMAX and RP splitting: (CTRL-7288)
        // https://support.emc.com/docu10627_RecoverPoint-Deploying-with-Symmetrix-Arrays-and-Splitter-Technical-Notes.pdf?language=en_US
        // We need to align the masking of volumes to hosts to the same ports as the RP masking view.
        portUsageMap = filterStoragePortsForRPVMAX(system.getId(), networkMap, varray, portUsageMap, volumeURIs);

        // Loop through all the required Networks, allocating ports as necessary.
        Map<NetworkLite, List<StoragePort>> portsAllocated = new HashMap<NetworkLite, List<StoragePort>>();
        for (URI netURI : orderedNetworks) {
            // This is the network of the initiator
            NetworkLite network = networkMap.get(netURI);
            Integer portsNeeded = net2PortsNeeded.get(netURI);
            if (portsNeeded == null || portsNeeded == 0) {
                _log.info("No ports to be assigned for net: " + netURI);
                continue;
            }

            List<Initiator> initiators = net2InitiatorsMap.get(netURI);
            // Check that there are initiators to get assignments. This check is
            // needed for when initiators were eliminate by #filterRemoteInitiators
            if (initiators == null || initiators.isEmpty()) {
                _log.info("No initiators to be assigned for net: " + netURI);
                continue;
            }

            if (portUsageMap.get(netURI).isEmpty()) {
                _log.info("No ports available for allocation net: " + netURI);
                throw PlacementException.exceptions.cannotAllocateRequestedPorts(
                        network.getLabel(), system.getNativeGuid(), portsNeeded, 0, 0);
            }
            // Allocate the storage ports.
            portsAllocated.put(network, allocatePortsFromNetwork(
                    system.getId(), network, varray, portsNeeded,
                    portUsageMap.get(netURI), allocator, existingPortsMap.get(netURI),
                    pathParams.getAllowFewerPorts()));
        }
        return portsAllocated;
    }

    /**
     * If this is RP and VMAX, we need to filter out storage ports that aren't part of the existing RP mask,
     * otherwise the masking operation will fail.
     * 
     * @param storage - storage system
     * @param networkMap - network map
     * @param varray - virtual array
     * @param portUsageMap - storage port map to draw from
     * @return portUsageMap updated
     */
    private Map<URI, Map<StoragePort, Long>> filterStoragePortsForRPVMAX(
            URI storage, Map<URI, NetworkLite> networkMap, URI varray,
            Map<URI, Map<StoragePort, Long>> portUsageMap,
            Collection<URI> volumeURIs) {
        // First, make sure this is VMAX
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storage);
        if (storageSystem == null || storageSystem.getSystemType() == null ||
                !StorageSystem.Type.vmax.name().equalsIgnoreCase(storageSystem.getSystemType()))
        {
            // Bail out, not VMAX
            return portUsageMap;
        }

        // Next, check to see if any volumes in this list is part of an RP export group already.
        if (volumeURIs == null || volumeURIs.isEmpty()) {
            // Bail out, no volumes specified. So definitely not RP.
            return portUsageMap;
        }

        // Log the port usage map before we modify it.
        logPortUsageMap(portUsageMap);

        for (URI volumeId : volumeURIs) {
            // Check to see if this volume is in an RP export group
            URIQueryResultList exportGroupURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory.getVolumeExportGroupConstraint(
                    volumeId), exportGroupURIs);
            while (exportGroupURIs.iterator().hasNext()) {
                URI exportGroupURI = exportGroupURIs.iterator().next();
                ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
                if (ExportUtils.checkIfExportGroupIsRP(exportGroup)) {
                    _log.info("filterStoragePortsForRPVMAX - Found that exporting volumes that are being split by VMAX.  "
                            + "Examining storage ports and qualifying only storage ports used in the RecoverPoint masking view(s)");
                    // Explore storage ports in the masking views and collect them.
                    Set<URI> rpTargetPorts = new HashSet<URI>();
                    if (exportGroup.getExportMasks() == null) {
                        continue;
                    }
                    for (String maskId : exportGroup.getExportMasks()) {
                        ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(maskId));
                        rpTargetPorts.addAll(Collections2.transform(mask.getStoragePorts(),
                                CommonTransformerFunctions.FCTN_STRING_TO_URI));
                    }

                    // Print up a good log message using the storage ports we found.
                    for (URI storagePortId : rpTargetPorts) {
                        List<StoragePort> ports = _dbClient.queryObjectField(StoragePort.class, "portName", Arrays.asList(storagePortId));
                        if (ports == null) {
                            continue;
                        }

                        StringSet portNames = new StringSet();
                        for (StoragePort port : ports) {
                            portNames.add(port.getPortName());
                        }
                        _log.info("filterStoragePortsForRPVMAX - Found that RP masking view is using ports: {}",
                                Joiner.on(',').join(portNames));
                    }

                    // Go through all of the storage ports in all networks and remove those that do not appear in this list.
                    Set<StoragePort> removePorts = new HashSet<StoragePort>();
                    for (Map.Entry<URI, Map<StoragePort, Long>> entry : portUsageMap.entrySet()) {
                        if (entry.getValue().keySet() == null) {
                            continue;
                        }
                        for (StoragePort port : entry.getValue().keySet()) {
                            if (!rpTargetPorts.contains(port.getId())) {
                                removePorts.add(port);
                                _log.info(
                                        "filterStoragePortsForRPVMAX - Found that RP masking view does not use port {}, "
                                                + "so it does not qualify for host/cluster export",
                                        port.getPortName());
                            } else {
                                _log.info(
                                        "filterStoragePortsForRPVMAX - Found that RP masking view uses port {}, "
                                                + "so it qualifies for host/cluster export",
                                        port.getPortName());
                            }
                        }
                    }

                    // Remove the storage ports from the usage map.
                    for (StoragePort removePort : removePorts) {
                        for (Map.Entry<URI, Map<StoragePort, Long>> entry : portUsageMap.entrySet()) {
                            entry.getValue().remove(removePort);
                        }
                    }
                }
            }
        }

        // Log the port usage map after we modify it.
        logPortUsageMap(portUsageMap);

        return portUsageMap;
    }

    private void logPortUsageMap(Map<URI, Map<StoragePort, Long>> portUsageMap) {
        // Print up the resulting port usage map.
        StringBuilder sb = new StringBuilder();
        sb.append("filterStoragePortsForRPVMAX - Resulting storage port map: ");
        for (Map.Entry<URI, Map<StoragePort, Long>> entry : portUsageMap.entrySet()) {
            if (entry.getValue().keySet() == null) {
                continue;
            }
            NetworkLite nl = NetworkUtil.getNetworkLite(entry.getKey(), _dbClient);
            Collection<String> portNames = transform(entry.getValue().keySet(), CommonTransformerFunctions.fctnStoragePortToPortName());
            sb.append(String.format("Network: %s, Ports: %s", nl.getLabel(), Joiner.on(',').join(portNames)));
        }
        _log.info(sb.toString());
    }

    /**
     * After all initiators are accounted for, for each host, check if the initiators
     * are routed or local and update the list:
     * <ul>
     * <li>If the host's initiators are all local, keep them all</li>
     * <li>If the host's initiators are all routed, keep them all</li>
     * <li>If the host's initiators are a mix of local and routed, remove the routed initiators</li>
     * </ul>
     * For a given network, if all initiators are not used, remove the network entry
     * from the map so that its ports are no longer considered in the port allocation
     * 
     * @param system the storage system for the export
     * @param varray the varray of the export
     * @param net2InitiatorsMap a map of network-to-initiators for the export request
     * @param networkMap Map of network URI to network, it must have the same keys as
     *            net2InitiatorsMap
     */
    private void filterRemoteInitiators(
            StorageSystem system, URI varray, Map<URI, List<Initiator>> net2InitiatorsMap, Map<URI, NetworkLite> networkMap) {
        Set<URI> localNetworks = getStorageSystemLocalNetworks(system, varray.toString());
        Map<URI, Map<URI, List<Initiator>>> hostInitiatorsMap = getHostInitiatorsMap(net2InitiatorsMap);
        Iterator<URI> itr = net2InitiatorsMap.keySet().iterator();

        while (itr.hasNext()) {
            URI key = itr.next();
            for (URI hostURI : hostInitiatorsMap.keySet()) {
                Collection<URI> hostNetworks = getHostFilteredNetworks(localNetworks, hostInitiatorsMap.get(hostURI).keySet());
                if (!hostNetworks.contains(key)) {
                    if (hostInitiatorsMap.get(hostURI).get(key) != null) {
                        _log.info("Removing initiators {} for host {} because they have routed access " +
                                " to the storage system while other initiators have local access.",
                                hostInitiatorsMap.get(hostURI).get(key),
                                hostURI);
                        net2InitiatorsMap.get(key).removeAll(hostInitiatorsMap.get(hostURI).get(key));
                    }
                }
            }
            if (net2InitiatorsMap.get(key).isEmpty()) {
                itr.remove();
                networkMap.remove(key);
            }
        }
    }

    /**
     * Given the list of host initiators networks and the local networks on the
     * storage system, filter out the remote networks if the host has local access
     * by local networks to the storage system
     * 
     * @param localNetworks the storage system local networks
     * @param hostNetworks the host networks as computed from its initiators
     * @return the list of networks that should be used for the host.
     */
    private Collection<URI> getHostFilteredNetworks(Set<URI> localNetworks, Set<URI> hostNetworks) {
        if (Collections.disjoint(localNetworks, hostNetworks)) { // all the networks have remote access, use all initiators
            return hostNetworks;
        } else {
            HashSet<URI> temp = new HashSet<URI>();
            for (URI net : hostNetworks) {
                if (localNetworks.contains(net)) {
                    temp.add(net);
                }
            }
            return temp;
        }
    }

    /**
     * Given a list of networks-to-initiators, further break down the map by host so
     * that the end result is a map of hosts-to-networks-to-initiators.
     * 
     * @param net2InitiatorsMap networks-to-initiators map
     * @return a map of hosts-to-network-to-initiators
     */
    private Map<URI, Map<URI, List<Initiator>>> getHostInitiatorsMap(Map<URI, List<Initiator>> net2InitiatorsMap) {
        Map<URI, Map<URI, List<Initiator>>> hostInitiatorsMap = new HashMap<URI, Map<URI, List<Initiator>>>();
        for (Map.Entry<URI, List<Initiator>> entry : net2InitiatorsMap.entrySet()) {
            List<Initiator> initiators = entry.getValue();
            for (Initiator initiator : initiators) {
                URI host = initiator.getHost();
                if (NullColumnValueGetter.isNullURI(host)) {
                    host = StoragePortsAssigner.unknown_host_uri;
                }
                Map<URI, List<Initiator>> hostMap = hostInitiatorsMap.get(host);
                if (hostMap == null) {
                    hostMap = new HashMap<URI, List<Initiator>>();
                    hostInitiatorsMap.put(host, hostMap);
                }
                if (hostMap.get(entry.getKey()) == null) {
                    hostMap.put(entry.getKey(), new ArrayList<Initiator>());
                }
                hostMap.get(entry.getKey()).add(initiator);
            }
        }
        return hostInitiatorsMap;
    }

    /**
     * For a given storage system, find all the networks that can be used to access
     * the storage system without routing in a given varray.
     * 
     * @param system the storage system
     * @param varray the varray
     * @return find all the networks that can access the storage
     *         system without routing
     */
    private Set<URI> getStorageSystemLocalNetworks(StorageSystem system, String varray) {
        Set<URI> networks = new HashSet<URI>();
        List<StoragePort> ports =
                ConnectivityUtil.getStoragePortsForSystem(_dbClient,
                        system.getId());
        for (StoragePort port : ports) {
            if (!NullColumnValueGetter.isNullURI(port.getNetwork())
                    && port.getTaggedVirtualArrays() != null &&
                    port.getTaggedVirtualArrays().contains(varray)) {
                networks.add(port.getNetwork());
            }
        }
        return networks;
    }

    private void checkPathParams(ExportPathParams pathParams, StorageSystem system) {
        // Make a reasonable default for ExportPathParams if not set.
        // For VNX, default is two ports per initiator. For others, one port per initiator.
        if (pathParams == null) {
            pathParams = ExportPathParams.getDefaultParams();
        }
        if (pathParams.getPathsPerInitiator() == 0) {
            if (pathParams.getMaxPaths() >= 2
                    && system.getSystemType().equals(DiscoveredDataObject.Type.vnxblock.name())) {
                pathParams.setPathsPerInitiator(2);
            } else {
                pathParams.setPathsPerInitiator(1);
            }
        }
        if (pathParams.getMinPaths() == 0) {
            pathParams.setMinPaths(1);
        }
    }

    /**
     * Convert assignment map of Initiators to StoragePorts to the URI values for each.
     * 
     * @param assignments Map<Initiator, List<StoragePort>
     * @return Map<URI, List<URI>> Map of Initiator URI to a list of StoragePort URIs
     */
    private Map<URI, List<URI>> convertAssignmentsToURIs(Map<Initiator, List<StoragePort>> assignments) {
        HashMap<URI, List<URI>> assignmentMap = new HashMap<URI, List<URI>>();
        for (Initiator initiator : assignments.keySet()) {
            URI key = initiator.getId();
            if (assignmentMap.get(key) == null) {
                assignmentMap.put(key, new ArrayList<URI>());
            }
            for (StoragePort port : assignments.get(initiator)) {
                assignmentMap.get(key).add(port.getId());
            }
        }
        return assignmentMap;
    }

    /**
     * Add new assignments to the zoning map.
     * 
     * @param assignments Map<Initiator, List<StoragePort> the new assignments
     * @param newZoningMap the new zoning map being generated.
     */
    private void addAssignmentsToZoningMap(Map<Initiator, List<StoragePort>> assignments, StringSetMap newZoningMap) {
        for (Initiator initiator : assignments.keySet()) {
            String key = initiator.getId().toString();
            List<StoragePort> ports = assignments.get(initiator);
            if (ports != null && !ports.isEmpty()) {
                if (newZoningMap.get(key) == null) {
                    newZoningMap.put(key, new StringSet());
                }
                if (ports != null) {
                    for (StoragePort port : ports) {
                        newZoningMap.get(key).add(port.getId().toString());
                    }
                }
            }
        }
    }

    /**
     * Allocate ports from a Network. It is assumed that the port usage metrics have
     * already been calculated.
     * 
     * @param storageURI Storage System
     * @param network NetworkLite
     * @param varrayURI Virtual Array
     * @param numPaths Number of ports to be allocated
     * @param portUsageMap The port usage map computed by computeStoragePortUsageMap
     * @param allocator The StoragePortsAllocator to use
     * @param previouslyAllocatedPorts A set of previously allocated ports in this Network
     * @param allowFewerPorts Boolean if true allows allocating fewer ports than requested
     * @return List of the allocated ports in an order for good redundancy.
     * @throws PlacementException
     */
    private List<StoragePort> allocatePortsFromNetwork(
            URI storageURI, NetworkLite network, URI varrayURI, int numPaths,
            Map<StoragePort, Long> portUsageMap,
            StoragePortsAllocator allocator, Set<StoragePort> previouslyAllocatedPorts,
            boolean allowFewerPorts) throws PlacementException {
        List<StoragePort> sports = new ArrayList<StoragePort>();
        if (network.getTransportType().equals(StorageProtocol.Transport.FC.name()) ||
                network.getTransportType().equals(StorageProtocol.Transport.IP.name())) {
            List<StoragePort> portList = allocator.selectStoragePorts(
                    _dbClient, portUsageMap, network, varrayURI, numPaths, previouslyAllocatedPorts, allowFewerPorts);
            for (StoragePort port : portList) {
                if (!sports.contains(port)) {
                    sports.add(port);
                }
            }
        } else {
            // Otherwise, follow the existing code and select one storage port per network
            List<StoragePort> spList = new ArrayList<StoragePort>();
            spList.addAll(portUsageMap.keySet());
            StoragePort storagePort = selectStoragePort(spList);
            if (!sports.contains(storagePort)) {
                sports.add(storagePort);
            }
        }
        return sports;
    }

    /**
     * Compute the ports available and their usage, and return a list of the proper
     * ordering of which networks to be processed first.
     * 
     * @param storageUri -- StorageSystem URI
     * @param networkMap -- a map of Network URI to NetworkLite indicating networks to process
     * @param varrayURI -- the Virtual Array URI
     * @param orderedNetworks -- OUT parameter: an ordered list of networks to process
     * @param storagePortsMap a map of network-to-ports of ports that can be allocated
     * @return -- a Map of Network URI to a Map of Storage Port to Long usage factor
     */
    private Map<URI, Map<StoragePort, Long>> computeStoragePortUsageMapForPorts(
            URI storageUri, Map<URI, NetworkLite> networkMap, URI varrayURI, List<URI> orderedNetworks,
            Map<NetworkLite, List<StoragePort>> storagePortsMap)
            throws PlacementException {
        Map<URI, Map<StoragePort, Long>> result = new HashMap<URI, Map<StoragePort, Long>>();
        PriorityQueue<NetworkUsage> usageQueue = new PriorityQueue<NetworkUsage>();
        // Then put them in the result map and the usageQueue.
        for (URI networkURI : networkMap.keySet()) {
            NetworkLite network = networkMap.get(networkURI);
            List<StoragePort> spList = storagePortsMap.get(network);
            if (spList == null || spList.isEmpty()) {
                throw PlacementException.exceptions.noStoragePortsInNetwork(network.getLabel());
            }
            if (network.getTransportType().equals(StorageProtocol.Transport.FC.name()) ||
                    network.getTransportType().equals(StorageProtocol.Transport.IP.name())) {
                Map<StoragePort, Long> portMap = computeStoragePortUsage(spList);
                // If there are no ports in the requested network, throw an error
                if (portMap.isEmpty()) {
                    throw PlacementException.exceptions.noStoragePortsInNetwork(network.getLabel());
                }
                result.put(networkURI, portMap);
                // Determine the port with the highest usage metric.
                Long max = Long.MIN_VALUE;
                for (Long x : portMap.values()) {
                    if (x > max) {
                        max = x;
                    }
                }
                // Put a NetworkUsage on the usageQueue.
                NetworkUsage netUsage = new NetworkUsage(networkURI, max);
                usageQueue.add(netUsage);
            } else {
                Map<StoragePort, Long> portUsage = new HashMap<StoragePort, Long>();
                for (StoragePort sp : spList) {
                    portUsage.put(sp, 0L);
                }
                result.put(networkURI, portUsage);
                // No usage metric. Just add to list.
                NetworkUsage netUsage = new NetworkUsage(networkURI, 0);
                usageQueue.add(netUsage);
            }
        }

        // Now generate an ordered list processing the networks with highest usage metric first.
        while (usageQueue.peek() != null) {
            orderedNetworks.add(usageQueue.poll().network);
        }
        return result;
    }

    /**
     * Find the storage port usage map for all the storage ports on the storage system
     * and the list of networks and a varray.
     * 
     * @param storageUri the storage system
     * @param networkMap the networks
     * @param varrayURI the varrays
     * @param orderedNetworks IN-OUT parameter
     * @return
     * @throws PlacementException
     */
// DEAD CODE ? TLW
//    private Map<URI, Map<StoragePort, Long>> computeStoragePortUsageMap(
//            URI storageUri, Map<URI, NetworkLite> networkMap, URI varrayURI, List<URI> orderedNetworks)
//            throws PlacementException {
//        Map<NetworkLite, List<StoragePort>> selectedStoragePortsMap =
//                selectStoragePortsInNetworks(storageUri, networkMap.values(), varrayURI);
//        return computeStoragePortUsageMapForPorts(storageUri, networkMap, varrayURI, orderedNetworks, selectedStoragePortsMap);
//    }

    /**
     * Inner class for sorting Network Usage.
     * Note that highest metric will be the highest priority (lowest value in compareTo).
     */
    class NetworkUsage implements Comparable {
        URI network;
        long metric;

        NetworkUsage(URI network, long metric) {
            this.network = network;
            this.metric = metric;
        }

        // Suppressing Sonar violation: This class overrides "equals()" and should therefore also override "hashCode()
        // CTRL-12976
        @SuppressWarnings("squid:S1206")
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NetworkUsage)) {
                return false;
            }
            NetworkUsage x = (NetworkUsage) obj;
            return this.metric == x.metric;
        }

        @Override
        public int compareTo(Object obj) {
            if (!(obj instanceof NetworkUsage)) {
                return 1;
            }
            NetworkUsage x = (NetworkUsage) obj;
            if (x.metric == this.metric) {
                return 0;
            }
            return (x.metric > this.metric ? 1 : -1);
        }
    };

    /**
     * Returns a list of Storage Port URIs for a list of StoragePorts.
     * 
     * @param ports List<StoragePort>
     * @return List<URI> of StoragePorts
     */
    private List<URI> getPortURIs(List<StoragePort> ports) {
        ArrayList<URI> uris = new ArrayList<URI>();
        for (StoragePort port : ports) {
            uris.add(port.getId());
        }
        return uris;
    }

    /**
     * Selects and returns the list targets (storage ports) that need to be removed from
     * an export mask when the initiators are removed from the storage group. If checks if
     * the targets are used by other initiators before they can be removed. It returns
     * an empty list if there are not any targets to remove.
     * 
     * @param mask the export mask from which the initiator will be removed
     * @param initiators the initiators being removed
     * @return a list of targets that are no longer needed by an initiators.
     */
    public List<URI> getRemoveInitiatorStoragePorts(
            ExportMask mask, List<Initiator> initiators) {
        // uris of ports candidates for removal
        Set<URI> portUris = new HashSet<URI>();
        List<URI> remainingInitiators = ExportUtils.getExportMaskAllInitiators(mask, _dbClient); // ask Ameer - do i need this function
        for (Initiator initiator : initiators) {
            portUris.addAll(ExportUtils.getInitiatorPortsInMask(mask, initiator, _dbClient));
            remainingInitiators.remove(initiator.getId());
        }

        // for the remaining initiators, get the networks and check if the ports are in use
        if (!remainingInitiators.isEmpty()) {
            Iterator<Initiator> remInitiators = _dbClient.queryIterativeObjects(Initiator.class,
                    remainingInitiators);
            List<URI> initiatorPortUris = null;
            while (remInitiators.hasNext()) {
                Initiator initiator = remInitiators.next();
                // stop looping if all the the ports are found to be in use
                if (portUris.isEmpty()) {
                    break;
                }
                initiatorPortUris = ExportUtils.getInitiatorPortsInMask(mask, initiator, _dbClient);
                _log.info("Ports {} are in use in by initiator {} ",
                        initiatorPortUris, initiator.getInitiatorPort());
                portUris.removeAll(initiatorPortUris);
            }
        }
        _log.info("Ports {} are going to be removed", portUris);
        return new ArrayList<URI>(portUris);
    }

    /**
     * Select a storage port from a list of all ports in transport zone and its subset of ports
     * already used for export.
     * 
     * TODO:
     * - select ports based on load
     * - select ports based on multipath requirement
     * - select ports based on fault domains
     * 
     * @param spList
     * @return
     */
    private StoragePort selectStoragePort(List<StoragePort> spList) {
        Collections.shuffle(spList);
        return spList.get(0);
    }

    /**
     * Return list of storage ports for the passed storage device connected
     * to the given network and with connectivity to the passed virtual
     * array.
     * 
     * Port must be REGISTERED, and the OperationalStatus must not be NOT_OK
     * and it must be a frontend port.
     * 
     * @param storageSystemURI The URI of the storage system
     * @param networkURI The URI of the network.
     * @param varrayURI The URI of the virtual array.
     * 
     * @return The list of storage ports.
     */
    public List<StoragePort> selectStoragePorts(URI storageSystemURI, URI networkURI, URI varrayURI) {
        NetworkLite networkLite = NetworkUtil.getNetworkLite(networkURI, _dbClient);
        _log.info("Selecting ports for network {} {}", networkLite.getLabel(), networkLite.getId());
        // The list of storage ports in networkURI
        List<StoragePort> spList = new ArrayList<StoragePort>();
        // The list of storage ports in networks that are routed to networkURI
        List<StoragePort> rspList = new ArrayList<StoragePort>();
        List<String> unroutedPorts = new ArrayList<String>();
        List<String> routedPorts = new ArrayList<String>();
        List<String> notRegisteredOrOk = new ArrayList<String>();
        List<String> notInVarray = new ArrayList<String>();
        List<String> wrongNetwork = new ArrayList<String>();

        URIQueryResultList sports = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getStorageDeviceStoragePortConstraint(storageSystemURI), sports);
        Iterator<URI> it = sports.iterator();
        while (it.hasNext()) {
            StoragePort sp = _dbClient.queryObject(StoragePort.class, it.next());
            if (sp.getInactive() || sp.getNetwork() == null
                    || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name().equals(sp.getCompatibilityStatus())
                    || !DiscoveryStatus.VISIBLE.name().equals(sp.getDiscoveryStatus())
                    || !sp.getRegistrationStatus().equals(StoragePort.RegistrationStatus.REGISTERED.name())
                    || StoragePort.OperationalStatus.NOT_OK.equals(StoragePort.OperationalStatus.valueOf(sp.getOperationalStatus()))
                    || StoragePort.PortType.valueOf(sp.getPortType()) != StoragePort.PortType.frontend) {
                _log.debug(
                        "Storage port {} is not selected because it is inactive, is not compatible, "
                                + "is not visible, has no network assignment, "
                                + "is not registered, has a status other than OK, or is not a frontend port", sp.getLabel());
                notRegisteredOrOk.add(portName(sp));
                continue;
            }

            // Make sure the storage port is in the passed network.

            if (sp.getNetwork().equals(networkURI) || (networkLite != null &&
                    networkLite.hasRoutedNetworks(sp.getNetwork()))) {
                // Now make sure the port has connectivity/assignment
                // to the passed virtual array.
                StringSet spVArrayIds = sp.getTaggedVirtualArrays();
                if (spVArrayIds != null && spVArrayIds.contains(varrayURI.toString())) {
                    if (sp.getNetwork().equals(networkURI)) {
                        spList.add(sp);
                        unroutedPorts.add(portName(sp));
                    } else {
                        _log.debug("Storage port {} is not in the requested network {} " +
                                "but it is routed to it.", sp.getNativeGuid(), networkURI);
                        rspList.add(sp);
                        // Duplicate list with just name for better error message.
                        routedPorts.add(portName(sp));
                    }
                } else {
                    _log.debug("Storage port {} not selected because it is not connected " +
                            "or assigned to requested virtual array {}", sp.getNativeGuid(), varrayURI);
                    notInVarray.add(portName(sp));
                }
            } else {
                _log.debug("Storage port {} not selected because it's network {} " +
                        "is not the requested network {}",
                        new Object[] { sp.getNativeGuid(), sp.getNetwork(), networkURI });
                wrongNetwork.add(portName(sp));
            }
        }
        if (!notRegisteredOrOk.isEmpty()) {
            _log.info("Ports not selected because they are inactive, have no network assignment, " +
                    "are not registered, bad operational status, or not type front-end: "
                    + Joiner.on(" ").join(notRegisteredOrOk));
        }
        if (!notInVarray.isEmpty()) {
            _log.info("Ports not selected because they are not assigned to the requested virtual array: "
                    + varrayURI + " " + Joiner.on(" ").join(notInVarray));
        }
        if (!wrongNetwork.isEmpty()) {
            _log.info("Ports not selected because they are not in the requested network: "
                    + networkURI + " " + Joiner.on(" ").join(wrongNetwork));
        }
        if (!rspList.isEmpty() && !spList.isEmpty()) {
            _log.info("Ports not selected because they are routed and local ports are available: "
                    + networkURI + " " + Joiner.on(" ").join(routedPorts));
        }
        _log.info("Ports that were selected: " +
                (spList.isEmpty() ? Joiner.on(" ").join(routedPorts) : Joiner.on(" ").join(unroutedPorts)));
        return spList.isEmpty() ? rspList : spList;
    }

    /**
     * Return list of storage ports for the passed storage device connected
     * to the given network and with connectivity to the passed virtual
     * array.
     * 
     * Port must be REGISTERED, and the OperationalStatus must not be NOT_OK
     * and it must be a frontend port.
     *
     * @param storageSystemURI The URI of the storage system
     * @param networkURI The URI of the network.
     * @param varrayURI The URI of the virtual array.
     * @param pathParams The ExportPathParameter settings which may contain a set of allowed ports. 
     *              Optional, can be null.
     * 
     * @return The list of storage ports.
     */
    public Map<NetworkLite, List<StoragePort>> selectStoragePortsInNetworks(URI storageSystemURI, Collection<NetworkLite> networks,
            URI varrayURI, ExportPathParams pathParams) {
        Map<NetworkLite, List<StoragePort>> portsInNetwork = new HashMap<NetworkLite, List<StoragePort>>();
        List<StoragePort> storagePorts = ExportUtils.getStorageSystemAssignablePorts(
                                                _dbClient, storageSystemURI, varrayURI, pathParams);
        for (NetworkLite networkLite : networks) {
            URI networkURI = networkLite.getId();
            _log.info("Selecting ports for network {} {}", networkLite.getLabel(), networkLite.getId());
            // The list of storage ports in networkURI
            List<StoragePort> spList = new ArrayList<StoragePort>();
            // The list of storage ports in networks that are routed to networkURI
            List<StoragePort> rspList = new ArrayList<StoragePort>();
            List<String> unroutedPorts = new ArrayList<String>();
            List<String> routedPorts = new ArrayList<String>();
            List<String> wrongNetwork = new ArrayList<String>();

            for (StoragePort sp : storagePorts) {
                // Make sure the storage port is in the passed network.
                if (sp.getNetwork().equals(networkURI) || (networkLite != null &&
                        networkLite.hasRoutedNetworks(sp.getNetwork()))) {
                    // Now make sure the port has connectivity/assignment
                    // to the passed virtual array.
                    if (sp.getNetwork().equals(networkURI)) {
                        spList.add(sp);
                        unroutedPorts.add(portName(sp));
                    } else {
                        _log.debug("Storage port {} is not in the requested network {} " +
                                "but it is routed to it.", sp.getNativeGuid(), networkURI);
                        rspList.add(sp);
                        // Duplicate list with just name for better error message.
                        routedPorts.add(portName(sp));
                    }
                } else {
                    _log.debug("Storage port {} not selected because it's network {} " +
                            "is not the requested network {}",
                            new Object[] { sp.getNativeGuid(), sp.getNetwork(), networkURI });
                    wrongNetwork.add(portName(sp));
                }
            }
            if (!wrongNetwork.isEmpty()) {
                _log.info("Ports not selected because they are not in the requested network: "
                        + networkURI + " " + Joiner.on(" ").join(wrongNetwork));
            }
            if (!rspList.isEmpty() && !spList.isEmpty()) {
                _log.info("Ports not selected because they are routed and local ports are available: "
                        + networkURI + " " + Joiner.on(" ").join(routedPorts));
            }
            _log.info("Ports that were selected: " +
                    (spList.isEmpty() ? Joiner.on(" ").join(routedPorts) : Joiner.on(" ").join(unroutedPorts)));
            portsInNetwork.put(networkLite, spList.isEmpty() ? rspList : spList);
        }
        return portsInNetwork;
    }

    /**
     * Returns a port name guaranteed to have the director identification.
     * 
     * @param port
     * @return
     */
    public static String portName(StoragePort port) {
        if (port.getPortName().startsWith(port.getPortGroup())) {
            return port.getPortName();
        } else {
            return port.getPortGroup() + ":" + port.getPortName();
        }
    }

    /**
     * Look up NetworkLite, given an endpoint and virtual array. It returns
     * active, registered networks that are of the requested transport type
     * only. To looking up an endpoint network without the added checks use {@link NetworkUtil#getEndpointNetworkLite(String, DbClient)}
     * 
     * 
     * @param dbClient Reference to a DB client.
     * @param transportType Transport type
     * @param endpoint The endpoint
     * @return NetworkLite reference if found, else null.
     */
    public static NetworkLite lookupNetworkLite(DbClient dbClient, StorageProtocol.Transport transportType,
            String endpoint) {
        try {
            NetworkLite netLite = NetworkUtil.getEndpointNetworkLite(endpoint, dbClient);
            if (netLite == null) {
                return null;
            }
            if (!netLite.registered()) {
                _log.info("Network lookup: the endpoint network is deregistered.");
                return null;
            }
            if (!netLite.getTransportType().equals(transportType.toString())) {
                _log.info("Network lookup: the endpoint network is not of transport type {}.", transportType.toString());
                return null;
            }
            return netLite;
        } catch (DatabaseException e) {
            _log.error("Network is not found for endpoint: ", endpoint);
        }
        _log.info("Network lookup: endpoint not found.");
        return null;
    }

    /**
     * Look up Network, given an endpoint and virtual array.
     * 
     * @param dbClient Reference to a DB client.
     * @param transportType Transport type
     * @param endpoint The endpoint
     * @return Network reference if found, else null.
     */
    public static Network lookupNetworkFull(DbClient dbClient, StorageProtocol.Transport transportType,
            String endpoint) {
        _log.info(String.format("Network lookup: type(%s), endpoint(%s)",
                transportType.name(), endpoint));
        try {
            return NetworkUtil.getEndpointNetwork(endpoint, dbClient);
        } catch (DatabaseException e) {
            _log.error("Network is not found for endpoint: ", endpoint);
        }
        _log.info("Network lookup: endpoint not found.");
        return null;
    }

    /**
     * Finds and returns the NetworkLite for an initiator
     * 
     * @param initiator
     * @param _dbClient an instance if {@link DbClient}
     * @return the Network (lite) that contains the initator's endpoint
     * @throws DeviceControllerException when a transport zone cannot be found
     */
    public static NetworkLite getInitiatorNetwork(Initiator initiator, DbClient _dbClient) {
        NetworkLite netLite = lookupNetworkLite(_dbClient, StorageProtocol.block2Transport(initiator.getProtocol()),
                initiator.getInitiatorPort());
        return netLite;
    }

    /**
     * Given the existingZoningMap from the ExportMask, get a map of
     * Initiators to a List<StoragePort> of the StoragePorts assigned to that Initiator.
     * 
     * Note: This is varray aware, so even if zoning map has storage ports for the initiator
     * but they are not in the varray that is been looked for then those ports are not returned
     * in the map. This will be the case where volumes from two different varrays are exported to
     * the same host and the storage ports in those varrays might be different.
     * 
     * @param existingZoningMap -- StringSetMap
     * @return Map<Initiator, List<StoragePort>> existing assignment map with Objects
     */
    Map<Initiator, List<StoragePort>> generateInitiatorsToStoragePortsMap(StringSetMap existingZoningMap, URI varray) {
        Map<Initiator, List<StoragePort>> initiatorsToStoragePortsMap = new HashMap<Initiator, List<StoragePort>>();
        if (existingZoningMap == null) {
            return initiatorsToStoragePortsMap;
        }
        for (String initiatorId : existingZoningMap.keySet()) {
            Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
            if (initiator == null || initiator.getInactive()) {
                continue;
            }
            NetworkLite network = getInitiatorNetwork(initiator, _dbClient);
            String networkLabel = (network != null ? network.getLabel() : "<unknown network>");
            StringSet ports = existingZoningMap.get(initiatorId);
            if (ports == null) {
                continue;
            }
            StringBuilder portNames = new StringBuilder();
            for (String portId : ports) {
                StoragePort port = _dbClient.queryObject(StoragePort.class, URI.create(portId));
                if (port != null && port.getTaggedVirtualArrays() != null
                        && port.getTaggedVirtualArrays().contains(varray.toString())
                        && port.getRegistrationStatus().toString()
                                .equals(DiscoveredDataObject.RegistrationStatus.REGISTERED.name())
                        && DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name().equals(port.getCompatibilityStatus().toString())
                        && DiscoveryStatus.VISIBLE.name().equals(port.getDiscoveryStatus().toString())) {
                    if (initiatorsToStoragePortsMap.get(initiator) == null) {
                        initiatorsToStoragePortsMap.put(initiator, new ArrayList<StoragePort>());
                    }
                    initiatorsToStoragePortsMap.get(initiator).add(port);
                    portNames.append(port.getPortName() + " (" + port.getPortNetworkId() + ")  ");
                }
            }
            _log.info(String.format("Existing initiator %s (%s) network %s ports %s",
                    initiator.getInitiatorPort(), initiator.getHostName(), networkLabel, portNames.toString()));
        }
        return initiatorsToStoragePortsMap;
    }

    /**
     * Creates a map of Initiator Network URI to a Set<StoragePort> of ports in that Network.
     * When one or more of the initiator have routed access to the storage ports allocated,
     * the storage ports mapped to the network may not all be in the network.
     * 
     * @param existingAssignments -- Map of Initiator to a list of Storage Port assignments
     * @param existingInitiatorsMap -- Map of network to initiators
     * @return Map of Network URI to set of Storage Ports in that Network
     */
    private static Map<URI, Set<StoragePort>> generateNetworkToStoragePortsMap(
            Map<Initiator, List<StoragePort>> existingAssignments, Map<URI, Set<Initiator>> existingInitiatorsMap) {
        Map<URI, Set<StoragePort>> network2StoragePortsMap = new HashMap<URI, Set<StoragePort>>();
        if (existingAssignments == null) {
            return network2StoragePortsMap;
        }
        for (Entry<URI, Set<Initiator>> networkInitiators : existingInitiatorsMap.entrySet()) {
            network2StoragePortsMap.put(networkInitiators.getKey(), new HashSet<StoragePort>());
            for (Initiator initiator : networkInitiators.getValue()) {
                List<StoragePort> ports = existingAssignments.get(initiator);
                for (StoragePort port : ports) {
                    if (port.getRegistrationStatus().toString()
                            .equals(DiscoveredDataObject.RegistrationStatus.REGISTERED.name())
                            && port.getCompatibilityStatus().toString()
                                    .equals(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                            && port.getDiscoveryStatus().toString().equals(DiscoveryStatus.VISIBLE.name())) {
                        network2StoragePortsMap.get(networkInitiators.getKey()).add(port);
                    }
                }
            }
        }
        return network2StoragePortsMap;
    }

    /**
     * Creates a map of Network URI to a Set<Initiator> initiators in that Network.
     * 
     * @param existingAssignments -- Map of Initiator to a list of Storage Port assignments
     * @return Map of Network URI to a set of Initiators in that Network
     */
    private static Map<URI, Set<Initiator>> generateNetworkToInitiatorsMap(
            Map<Initiator, List<StoragePort>> existingAssignments, DbClient dbClient) {
        Map<URI, Set<Initiator>> network2InitiatorsMap = new HashMap<URI, Set<Initiator>>();
        if (existingAssignments == null) {
            return network2InitiatorsMap;
        }
        NetworkLite network = null;
        for (Initiator initiator : existingAssignments.keySet()) {
            network = getInitiatorNetwork(initiator, dbClient);
            if (network == null) {
                continue;
            }
            if (network2InitiatorsMap.get(network.getId()) == null) {
                network2InitiatorsMap.put(network.getId(), new HashSet<Initiator>());
            }
            network2InitiatorsMap.get(network.getId()).add(initiator);
        }
        return network2InitiatorsMap;
    }

    /**
     * Emits an info message about an initiator containing the address, id, and network name.
     * 
     * @param initiator
     * @param network
     */
    public static void logInitiator(Initiator initiator, NetworkLite network) {
        String networkName = (network != null ? network.getLabel() : "<unknown network>");
        _log.info(String.format("Attempting to assign port(s) to initiator: %s (%s) in network: %s",
                initiator.getInitiatorPort(), initiator.getHostName(), networkName));
    }

    /**
     * Returns a list of StoragePort URIs (with no duplicates) from the
     * assignments map returned by assignStoragePorts and the preZonedZoningMap
     * that contains old assignments as well as any pre-zoned ports that were assigned.
     * 
     * @param assignments -- the ports that were assigned
     * @return list URI of assigned port with no duplicates
     */
    static public List<URI> getTargetURIsFromAssignments(Map<URI, List<URI>> assignments) {
        Set<URI> targets = new HashSet<URI>();
        for (List<URI> portList : assignments.values()) {
            targets.addAll(portList);
        }
        return new ArrayList<URI>(targets);
    }

    /**
     * Returns a set of StoragePort id strings from assignments.
     * 
     * @param assignments map returned by exportMask.getZoningMap()
     * @return Set<String> ids of targets
     */
    static public Set<String> getTargetIdsFromAssignments(StringSetMap assignments) {
        Set<String> targets = new HashSet<String>();
        if (assignments == null || assignments.isEmpty()) {
            return targets;
        }
        for (Set<String> targetSet : assignments.values()) {
            targets.addAll(targetSet);
        }
        return targets;
    }

    /**
     * Calculates the path parameters for an existing ExportMask.
     * This has to be calculated per host, since maxPaths is per host.
     * 
     * @param dbClient
     * @param mask
     * @return ExportPathParams with calculated values
     */
    public static ExportPathParams calculateExportPathParamForExportMask(DbClient dbClient, ExportMask mask) {
        Map<String, Integer> hostInitiatorCounts = new HashMap<String, Integer>();
        // Calculate the path parameters.
        ExportPathParams param = new ExportPathParams(0, 0, 0);
        // If there is a zoningMap, use that.
        if (mask.getZoningMap() != null) {
            for (String initiatorId : mask.getZoningMap().keySet()) {
                Initiator initiator = dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                if (initiator == null || initiator.getInactive()) {
                    continue;
                }
                String host = (initiator.getHost() != null) ? initiator.getHost().toString() : "<unknown>";
                if (hostInitiatorCounts.get(host) == null) {
                    hostInitiatorCounts.put(host, 0);
                }
                Set<String> portIds = mask.getZoningMap().get(initiatorId);
                if (portIds == null) {
                    continue;
                }
                int ppi = 0;
                for (String portId : portIds) {
                    Integer newValue = hostInitiatorCounts.get(host) + 1;
                    hostInitiatorCounts.put(host, newValue);
                    ppi++;
                }
                if (ppi > param.getPathsPerInitiator()) {
                    param.setPathsPerInitiator(ppi);
                }
            }
            // Return the maximum of any host.
            for (Integer value : hostInitiatorCounts.values()) {
                if (value > param.getMaxPaths()) {
                    param.setMaxPaths(value);
                }
            }
        } else {
            // If there is not a zoning map, we won't change things.
            _log.info(String.format("No zoning map for mask %s (%s), will not change zoning",
                    mask.getMaskName(), mask.getId()));
            param.setMaxPaths(Integer.MAX_VALUE);
        }
        return param;
    }

    /**
     * Given a collection of volume URIs, generates the ExportPathParam
     * values for all volumes (block objects)
     * in the collection. These are assumed to belong to (or about to belong to) one ExportMask.
     * The maxPath value from any of the volumes will be returned, along with
     * the corresponding pathsPerInitiator.
     * 
     * @param blockObjectURIs Collection<URI>
     * @param overrideNumPaths - if greater than zero, will override the calculation and be returned.
     * @return numPaths
     */
//    public ExportPathParams calculateExportPathParmForVolumes(Collection<URI> blockObjectURIs,
//            Integer overrideNumPaths) {
//        return calculateExportPathParamForVolumes(blockObjectURIs, overrideNumPaths, null);
//    }

    /**
     * Given a collection of volume URIs, generates the ExportPathParam
     * values for all volumes (block objects) in the collection.
     * These are assumed to belong to (or about to belong to) one ExportMask.
     * The maxPath value from any of the volumes will be returned, along with
     * the corresponding pathsPerInitiator.
     * 
     * @param blockObjectURIs Collection<URI>
     * @param overrideNumPaths - if greater than zero, will override the calculation and be returned.
     * @param storageSystemURI URI of Storage System, if not null, filters out
     *            BlockObjects created on other systems
     * @param exportGroupURI exportGroupURI
     * @return numPaths
     */
    public ExportPathParams calculateExportPathParamForVolumes(Collection<URI> blockObjectURIs,
            Integer overrideNumPaths, URI storageSystemURI, URI exportGroupURI) {
        ExportPathParams param = new ExportPathParams(0, 0, 0);
        // Look up the exportGroup
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
        // If overrideNumPaths is set, do that with pathsPerInitiator=2
        if (overrideNumPaths != null && overrideNumPaths > 0) {
            param = new ExportPathParams(overrideNumPaths, 0, 0);
            param.setAllowFewerPorts(true);
            return param;
        }
        if (blockObjectURIs != null) {
            for (URI uri : blockObjectURIs) {
                BlockObject blockObject = BlockObject.fetch(_dbClient, uri);
                if (blockObject == null) {
                    continue;
                }
                if (storageSystemURI != null &&
                        !storageSystemURI.equals(blockObject.getStorageController())) {
                    continue;
                }
                
                ExportPathParams volParam = null;
                if (exportGroup != null) {
                    // Check to see if the ExportGroup has path parameters for volume
                    if (exportGroup.getPathParameters().containsKey(uri.toString())) {
                        URI exportPathParamsUri = URI.create(exportGroup.getPathParameters().get(uri.toString()));
                        volParam = _dbClient.queryObject(ExportPathParams.class, exportPathParamsUri);
                    }
                }
                if (volParam == null) {
                    // Otherwise check use the Vpool path parameters
                    URI vPoolURI = getBlockObjectVPoolURI(blockObject, _dbClient);
                    volParam = getExportPathParam(blockObject, vPoolURI, _dbClient);
                }
                if (volParam.getMaxPaths() > param.getMaxPaths()) {
                    param = volParam;
                }
            }
        }

        if (param.getMaxPaths() == 0) {
            param = ExportPathParams.getDefaultParams();
        }
        return param;
    }

    /**
     * Get the ExportPathParams (maxPaths and pathsPerInitiator variables)
     * from the VirtualPool belonging to a volume
     * (or the parent volume of a snapshot).
     * For backward compatibility, if the fields other than num_paths in the
     * Vpool are empty, they are defaulted.
     * 
     * @TODO For ingestion, if there are more than 1 supported virtual pool,
     *       then consider the path params with least path settings.
     * 
     * @param block
     * @return Integer num_paths from VirtualPool
     */
    public static ExportPathParams getExportPathParam(BlockObject block, URI vPoolURI, DbClient dbClient) {
        if (vPoolURI == null) {
            return ExportPathParams.getDefaultParams();
        }
        VirtualPool vPool = dbClient.queryObject(VirtualPool.class, vPoolURI);
        if (vPool == null) {
            return ExportPathParams.getDefaultParams();
        }
        if (vPool.getNumPaths() == null) {
            return ExportPathParams.getDefaultParams();
        }
        Integer minPaths = vPool.getMinPaths();
        if (minPaths == null) {
            minPaths = 0;
        }
        Integer pathsPerInitiator = vPool.getPathsPerInitiator();
        if (pathsPerInitiator == null) {
            pathsPerInitiator = 0;
        }
        return new ExportPathParams(vPool.getNumPaths(), minPaths, pathsPerInitiator);
    }

    /**
     * Computes the usage of a set of candidate StoragePorts.
     * This is done by finding all the ExportMasks containing the ports, and then
     * totaling the number of Initiators across all masks that are using the port.
     * 
     * @param candidatePorts
     * @return Map of StoragePort to Integer usage metric that is count of Initiators using port
     */
    public Map<StoragePort, Long> computeStoragePortUsage(List<StoragePort> candidatePorts) {
        Map<StoragePort, Long> usages = new HashMap<StoragePort, Long>();
        if (candidatePorts.isEmpty()) {
            return usages;
        }
        StorageSystem system = _dbClient.queryObject(StorageSystem.class, candidatePorts.get(0).getStorageDevice());
        // This is needed for the API path, which will not have a PortMetricsProcessor from Spring injection
        return _portMetricsProcessor.computeStoragePortUsage(candidatePorts, system, true);
    }

    /**
     * Validate against minPaths for each host.
     * Additionally, if the Export type is Initiator, verify all initiators are assigned ports.
     * 
     * @param pathParams
     * @param hostInitiatorsMap
     * @param assignments
     */
    private void validateMinPaths(ExportPathParams pathParams,
            Map<URI, List<Initiator>> hostInitiatorsMap, Map<Initiator, List<StoragePort>> assignments) {
        // Do not validate ExportGroup Initiator type exports
        if (pathParams.returnExportGroupType() == null
                || pathParams.returnExportGroupType().equals(ExportGroup.ExportGroupType.Initiator)) {
            return;
        }
        for (URI hostURI : hostInitiatorsMap.keySet()) {
            // Sum up the ports used for this host.
            String hostName = "<unknown>";
            int unassignedInitiators = 0;
            int totalPorts = 0;
            for (Initiator initiator : hostInitiatorsMap.get(hostURI)) {
                if (initiator.getHostName() != null) {
                    hostName = initiator.getHostName();
                }
                List<StoragePort> ports = assignments.get(initiator);
                if (ports == null || ports.isEmpty()) {
                    unassignedInitiators++;
                }
                if (ports != null) {
                    totalPorts += ports.size();
                }
            }
            if (totalPorts < pathParams.getMinPaths()) {
                _log.info(String.format("Host %s (%s) has fewer ports assigned %d than min_paths %d",
                        hostName, hostURI, totalPorts, pathParams.getMinPaths()));
                throw PlacementException.exceptions.hostHasFewerThanMinPaths(
                        hostName, hostURI.toString(), totalPorts, pathParams.getMinPaths());
            }
            if (pathParams.returnExportGroupType() == ExportGroupType.Initiator && unassignedInitiators > 0) {
                _log.info(String.format("Host %s (%s) has %d initiators that were not assigned ports even though type Initiator",
                        hostName, hostURI, unassignedInitiators));
                throw PlacementException.exceptions.hostHasUnusedInitiators(hostName, hostURI.toString());
            }
        }
    }

    /**
     * Validates that at least two HA paths were allocated if there is more than one assignment.
     * 
     * @param pathParams -- the ExportPath parameters
     * @param assignments -- Map of Initiator to List of Storage Ports
     */
    private void validateHACapabilities(ExportPathParams pathParams, Map<Initiator, List<StoragePort>> assignments) {
        // Do not validate ExportGroup Initiator type exports
        if (pathParams.returnExportGroupType() == null
                || pathParams.returnExportGroupType().equals(ExportGroup.ExportGroupType.Initiator)) {
            return;
        }
        Set<URI> haDomains = null;
        Map<URI, List<Initiator>> initiatorsByHostMap = getInitiatorsByHostMap(assignments.keySet());
        for (List<Initiator> initiators : initiatorsByHostMap.values()) {
            haDomains = new HashSet<URI>();
            int portCount = 0;
            // Max paths must be two or greater
            if (pathParams.getMaxPaths() < 2) {
                continue;
            }
            for (Initiator initiator : initiators) {
                for (StoragePort port : assignments.get(initiator)) {
                    portCount++;
                    haDomains.add(port.getStorageHADomain());
                }
            }
            // If two or more ports were allocated, but less than two HA domains, throw PlacementException
            if (portCount >= 2 && haDomains.size() < 2) {
                throw PlacementException.exceptions.insufficientRedundancy(pathParams.getMaxPaths(), haDomains.size());
            }
        }
    }

    /**
     * Returns a StringSetMap containing the Initiator to StoragePort URIs.
     * 
     * @param assignments Map<URI, List<URI>>
     * @return StringSetMap with same information encoded
     */
    public static StringSetMap getZoneMapFromAssignments(Map<URI, List<URI>> assignments) {
        StringSetMap zoneMap = new StringSetMap();
        for (URI initiatorURI : assignments.keySet()) {
            StringSet portIds = new StringSet();
            List<URI> portURIs = assignments.get(initiatorURI);
            for (URI portURI : portURIs) {
                portIds.add(portURI.toString());
            }
            zoneMap.put(initiatorURI.toString(), portIds);
        }
        return zoneMap;
    }

    /**
     * Updates the ExportMask's zoning map after the initiator to port associations
     * have been discovered from an array like Cinder. This routine is needed when we
     * are masking first, and cannot tell the array what ports are assigned to what initiators,
     * i.e. rather the array tells us what it did.
     * This routine is not needed when the array can be told what initiators to map to what ports.
     * 1. All zoning map entries for the initiators in the mask are removed.
     * 2. For the targets in the mask, they are paired with the initiators they can service,
     * i.e. that are on the same or a routeable network, and are usable in the varray,
     * and the corresponding zones are put in the zoning map.
     * Then the path parameters are enforced, based on the path parameter data discovered from
     * the ExportMask.
     * 3. Any initiators with more than paths_per_initiator ports are reduced to have
     * only pathsPerInitiator number of ports. We try not to remove the same port from
     * multiple initiators. (Note: we do not declare it an error if there are fewer
     * than pathsPerInitiator ports assigned to an initiator.)
     * 4. Then we verify we are not over maxPaths. This is done by counting up the number of
     * paths currently in the mask, and if there are excess, cycling through the networks
     * (starting with the network with the most initiators) and removing initiators until
     * we are under paths per initiator.
     * 5. Finally we sum up the paths in the mask, and verify that we have at least as
     * many as minPaths.
     * 
     * @param mask -- The ExportMask being manipulated
     * @param varray -- The Virtual Array (normally from the ExportGroup)
     * @param exportGroupURI -- URI of the ExportGroup
     * 
     *            Assumption: the export mask has up to date initiators and storage ports
     */
    public void updateZoningMap(ExportMask mask, URI varray, URI exportGroupURI) {
        // Convert the volumes to a Collection.
        List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(mask);
        // Determine the number of paths required for the volumes in the export mask.
        ExportPathParams pathParams = calculateExportPathParamForVolumes(
                volumeURIs, 0, mask.getStorageDevice(), exportGroupURI);
        _log.info(String.format("Updating zoning map for ExportMask %s (%s) pathParams %s",
                mask.getMaskName(), mask.getId(), pathParams.toString()));

        // Data structures for mapping Network to Initiators and Network to StoragePorts
        Map<URI, Set<Initiator>> network2InitiatorsMap = new HashMap<URI, Set<Initiator>>();
        Map<URI, Set<StoragePort>> network2PortsMap = new HashMap<URI, Set<StoragePort>>();

        _log.debug("Export Mask before zoning map update -" + mask.toString());

        // Make a new zoning map.getDefaultParams
        // Remove all zoning map entries and set the current zoning map to null
        // so it is not considered by getInitiatorPortsInMask().
        for (String initiatorURIStr : mask.getZoningMap().keySet()) {
            mask.removeZoningMapEntry(initiatorURIStr);
        }
        mask.setZoningMap(null);
        // Loop through the Initiators, looking for ports in the mask
        // corresponding to the Initiator.
        for (String initiatorURIStr : mask.getInitiators()) {
            Initiator initiator = _dbClient.queryObject(Initiator.class,
                    URI.create(initiatorURIStr));
            if (initiator == null || initiator.getInactive()) {
                continue;
            }
            // Add the initiator to the net2InitiatorsMap
            NetworkLite initiatorNetwork = getInitiatorNetwork(initiator, _dbClient);
            if (!network2InitiatorsMap.containsKey(initiatorNetwork.getId())) {
                network2InitiatorsMap.put(initiatorNetwork.getId(), new HashSet<Initiator>());
            }
            network2InitiatorsMap.get(initiatorNetwork.getId()).add(initiator);
            List<URI> storagePortList = ExportUtils.getPortsInInitiatorNetwork(
                    mask, initiator, _dbClient);
            if (storagePortList.isEmpty()) {
                continue;
            }
            StringSet storagePorts = new StringSet();
            for (URI portURI : storagePortList) {
                StoragePort port = _dbClient.queryObject(StoragePort.class, portURI);
                URI portNetworkId = port.getNetwork();
                if (!DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                        .equals(port.getCompatibilityStatus())
                        || !DiscoveryStatus.VISIBLE.name().equals(port.getDiscoveryStatus())
                        || NullColumnValueGetter.isNullURI(portNetworkId)
                        || !port.getRegistrationStatus().equals(
                                StoragePort.RegistrationStatus.REGISTERED.name())
                        || StoragePort.OperationalStatus.NOT_OK
                                .equals(StoragePort.OperationalStatus
                                        .valueOf(port.getOperationalStatus()))
                        || StoragePort.PortType.valueOf(port.getPortType()) != StoragePort.PortType.frontend) {
                    _log.debug(
                            "Storage port {} is not selected because it is inactive, is not compatible, is not visible, not on a network, "
                                    + "is not registered, has a status other than OK, or is not a frontend port",
                            port.getLabel());
                    continue;
                }
                // If the port can be used in the varray,
                // include it in the zone map port entries for the initiator.
                // Network cnnectivity was checked in getInitiatorPortsInMask()
                if (port.getTaggedVirtualArrays().contains(varray.toString())) {
                    storagePorts.add(portURI.toString());
                    if (!network2PortsMap.containsKey(portNetworkId)) {
                        network2PortsMap.put(portNetworkId, new HashSet<StoragePort>());
                    }
                    network2PortsMap.get(portNetworkId).add(port);
                } else {
                    _log.debug(
                            "Storage port {} is not selected because it is not in the specified varray {}",
                            port.getLabel(), varray.toString());
                }
            }
            mask.addZoningMapEntry(initiatorURIStr, storagePorts);
        }

        _log.debug("Export Mask after zoning map update -" + mask.toString());

        // Now that we have constructed an initial cut at the zoning map, enforce the path parameters.
        // 1. Ensure that no initiator has more than the paths_per_initiator variable allows.
        // For every initiator, make sure it doesn't have more than paths_per_initiator ports.
        // Try not to remove the same port multiple times.
        Set<String> removedPorts = new HashSet<String>();
        for (URI networkURI : network2InitiatorsMap.keySet()) {
            for (Initiator initiator : network2InitiatorsMap.get(networkURI)) {
                StringSet ports = mask.getZoningMap().get(initiator.getId().toString());
                if ((null == ports) || (ports.size() <= pathParams.getPathsPerInitiator())) {
                    continue;
                }
                _log.info(String.format("Limiting paths for initiator %s to %s; initial ports %s",
                        initiator.getInitiatorPort(), pathParams.getPathsPerInitiator().toString(), ports));
                boolean removedPort = true;
                outer: while (removedPort && ports.size() > pathParams.getPathsPerInitiator()) {
                    // First try not removing an already removed port
                    removedPort = false;
                    for (String port : ports) {
                        if (!removedPorts.contains(port)) {
                            removedPorts.add(port);
                            ports.remove(port);
                            removedPort = true;
                            continue outer;
                        }
                    }
                    // As a last resort, remove a port that is duplicated
                    for (String port : ports) {
                        removedPorts.add(port);
                        ports.remove(port);
                        removedPort = true;
                        continue outer;
                    }
                }
                _log.info(String.format("Limited ports for initiator %s to %s", initiator.getInitiatorPort(), ports));
            }
        }

        // Now check that the total number of entries is not higher than maxPaths.
        // Remove paths from Networks with the most initiators to the list by removing initiators.
        ExportPathParams currentPathParams = calculateExportPathParamForExportMask(_dbClient, mask);
        Integer overMaxPaths = currentPathParams.getMaxPaths() - pathParams.getMaxPaths();

        // Make a sorted map of initiator count to networks.
        SortedMap<Integer, Set<URI>> initiatorCountToNetwork = new TreeMap<Integer, Set<URI>>();
        for (URI networkURI : network2InitiatorsMap.keySet()) {
            Integer count = network2InitiatorsMap.get(networkURI).size();
            if (!initiatorCountToNetwork.containsKey(count)) {
                initiatorCountToNetwork.put(count, new HashSet<URI>());
            }
            initiatorCountToNetwork.get(count).add(networkURI);
        }

        while (overMaxPaths > 0) {
            // Go backwards from last key (highest count) to first (lowest count).
            Integer lastKey = initiatorCountToNetwork.lastKey();
            Integer firstKey = initiatorCountToNetwork.firstKey();
            for (Integer count = lastKey; overMaxPaths > 0 && count >= firstKey; count--) {
                // Remove an Initiator from each network
                Set<URI> networks = initiatorCountToNetwork.get(count);
                if (networks == null) {
                    continue;
                }
                for (URI networkURI : networks) {
                    Iterator<Initiator> iter = network2InitiatorsMap.get(networkURI).iterator();
                    if (iter.hasNext()) {
                        // Remove an initiator
                        Initiator initiator = iter.next();
                        StringSet ports = mask.getZoningMap().get(initiator.getId().toString());
                        overMaxPaths -= ports.size();
                        _log.info(String.format("Removing initiator %s to comply with maxPaths", initiator.getInitiatorPort()));
                        mask.removeZoningMapEntry(initiator.getId().toString());
                        network2InitiatorsMap.get(networkURI).remove(initiator);
                    }
                    if (overMaxPaths <= 0) {
                        break;
                    }
                }
            }
        }

        // Finally, count the resulting number of paths, and make sure it is over minPaths.
        Integer pathCount = 0;
        Integer initiatorCount = 0;
        for (String initiatorId : mask.getZoningMap().keySet()) {
            initiatorCount++;
            StringSet ports = mask.getZoningMap().get(initiatorId);
            pathCount += ports.size();
        }
        _log.info(String.format("ExportMask %s (%s) pathCount %s", mask.getMaskName(), mask.getId(), pathCount.toString()));
        if (pathCount < pathParams.getMinPaths()) {
            throw PlacementException.exceptions.cannotAllocateMinPaths(
                    pathParams.getMinPaths(), initiatorCount,
                    pathParams.getPathsPerInitiator(), pathParams.getMinPaths(), pathParams.getMaxPaths());
        }

        // Save the updated ExportMask
        _dbClient.updateAndReindexObject(mask);
    }

    /**
     * Returns true if validateHACapabilities needs to be run
     * For some of the Hitachi models like AMS & HUS series don't support
     * clusters and hence redundancy couldn't be achieved across clusters
     * and hence bypassing this check for these models.
     * 
     * @param system [in] - StorageSystem object
     * @return true - if validateHACapabilities should be run
     */
    private boolean needToValidateHA(StorageSystem system) {
        // For now ScaleIO systems are the only ones that do not require
        // the validation, since port selection is meaningless (it's all
        // IP network based connectivity)
        // This check is needed for arrays with single storage HA domain
        return (!(system.getSystemType().equals(DiscoveredSystemObject.Type.scaleio.name()))
                && !(system.getSystemType().equals(DiscoveredSystemObject.Type.xtremio.name()))
                && (HDSUtils.checkForAMSSeries(system) || HDSUtils.checkForHUSSeries(system)));
    }

    /**
     * Groups initiators by host and returns a map of host-uri-to-initiators-list
     * 
     * @param initiators initiators to sort
     * @return a map of host URIs to initiators for that host
     */
    public static Map<URI, List<Initiator>> getInitiatorsByHostMap(Collection<Initiator> initiators) {
        List<Initiator> hostInitiators = null;
        Map<URI, List<Initiator>> map = new HashMap<URI, List<Initiator>>();
        if (initiators != null) {
            for (Initiator initiator : initiators) {
                URI hostURI = initiator.getHost() == null ?
                        URI.create(initiator.getHostName().replaceAll("\\s", "")) : initiator.getHost();
                if (NullColumnValueGetter.isNullURI(hostURI)) {
                    hostURI = StoragePortsAssigner.unknown_host_uri;
                }
                hostInitiators = map.get(hostURI);
                if (hostInitiators == null) {
                    hostInitiators = new ArrayList<Initiator>();
                    map.put(hostURI, hostInitiators);
                }
                hostInitiators.add(initiator);
            }
        }
        return map;
    }

    /**
     * This function wraps the two calls necessary to get port assignments, the first one is getting
     * pre-zoned ports assigned followed by the call to add any additional ports.
     * 
     * @param storage the storage system where the mask will be or was created
     * @param exportGroup the export group of the mask
     * @param initiators the initiators being added to the mask.
     * @param existingZoningMap this is the zoning map for an existing mask to which
     *            initiators are being added. It is null for new masks.
     * @param pathParams the export group aggregated path parameter
     * @param volumeURIs the volumes in the export mask
     * @param virtualArrayUri the URI of the export virtual array
     * @param token the workflow step Id
     * @return a map of existing zones paths between the storage system ports and the
     *         mask initiators.
     * @return
     */
    public Map<URI, List<URI>> assignStoragePorts(StorageSystem storage, ExportGroup exportGroup,
            List<Initiator> initiators, StringSetMap existingZoningMap,
            ExportPathParams pathParams, Collection<URI> volumeURIs,
            NetworkDeviceController networkDeviceController, URI virtualArrayUri, String token) {
        StringSetMap preZonedZoningMap = assignPrezonedStoragePorts(storage, exportGroup, initiators,
                existingZoningMap, pathParams, volumeURIs, networkDeviceController, virtualArrayUri, token);
        Map<URI, List<URI>> assignments =
                assignStoragePorts(storage, virtualArrayUri, initiators,
                        pathParams, preZonedZoningMap, volumeURIs);

        ExportUtils.addPrezonedAssignments(existingZoningMap, assignments, preZonedZoningMap);
        return assignments;
    }

    /**
     * Find the existing zones for a list of initiators. The initiators can be for a mask
     * that is being created or for a mask to which new initiators are added. In the latter
     * case the list of initiators would include only what is being added to the mask.
     * <p>
     * This existing zoning map returned by this function will be used by the port allocation code to give already zoned port priority over
     * other ports.
     * <p>
     * This function will only do its work if the config item controller_port_alloc_by_metrics_only is set to false. The default value of
     * the config is true.
     * <p>
     * This function will find existing zones for the list of initiators on the network system for all the storage system assignable ports.
     * If duplicate zones are found for an initiator-port pair, the existing zone selection algorithm is applied.
     * <p>
     * If zones were found on the network systems for the initiators and ports, there can be 3 possible scenarios
     * <ol>
     * <li>the number of existing paths is equal that what is requested in the vpool, in this case the port allocation/assignment code would
     * still be invoked but it will return no additional assignments</li>
     * <li>the number of existing paths is less that what is requested in the vpool, additional assignments will be made by the port
     * allocation/assignment code</li>
     * <li>Not all existing paths should be used, for example there are more paths than requested by the vpool or it could be that some
     * initiators have more paths that requested. The port allocation code is invoked to select the most favorable paths of the existing
     * ones.</li>
     * </ol>
     * Note that by using existing zones, the path-per-initiator may be violated.
     * 
     * @param storage the storage system where the mask will be or was created
     * @param exportGroup the export group of the mask
     * @param initiators the initiators being added to the mask.
     * @param existingZoningMap this is the zoning map for an existing mask to which
     *            initiators are being added. It is null for new masks.
     * @param pathParams the export group aggregated path parameter
     * @param volumeURIs the volumes in the export mask
     * @param virtualArrayUri the URI of the export virtual array
     * @param token the workflow step Id
     * @return a map of existing zones paths between the storage system ports and the
     *         mask initiators.
     */
    public StringSetMap assignPrezonedStoragePorts(StorageSystem storage, ExportGroup exportGroup,
            List<Initiator> initiators, StringSetMap existingZoningMap,
            ExportPathParams pathParams, Collection<URI> volumeURIs,
            NetworkDeviceController networkDeviceController, URI virtualArrayUri, String token) {
        // Prime the new zoning map with existing ones
        StringSetMap newZoningMap = new StringSetMap();
        if (existingZoningMap != null) {
            newZoningMap.putAll(existingZoningMap);
        }

        // check if this is a backend export
        boolean backend = ExportMaskUtils.areBackendInitiators(initiators);

        // Adjust the paths param based on whether at the end of this call the ports selected should meet the paths requirement
        ExportPathParams prezoningPathParams = getPrezoningPathParam(virtualArrayUri, pathParams, storage, backend);
        try {
            if (networkDeviceController == null) {
                return newZoningMap;
            }
            if (!NetworkUtil.areNetworkSystemDiscovered(_dbClient)) {
                _log.info("Cannot discover existing zones. There are no network systems discovered.");
                return newZoningMap;
            }

            if (!_networkScheduler.portAllocationUseExistingZones(storage.getSystemType(), backend)) {
                _log.info("The system configuration requests port selection to be based on metrics only "
                        + "i.e. ignore existing zones when selecting ports.");
                return newZoningMap;
            }

            _log.info("Checking for existing zoned ports for export {} before invoking port allocation.",
                    exportGroup.getGeneratedName());
            List<Initiator> newInitiators = new ArrayList<Initiator>();
            for (Initiator initiator : initiators) {
                if (!newZoningMap.containsKey(initiator.getId().toString())) {
                    newInitiators.add(initiator);
                }
            }

            Map<Initiator, List<StoragePort>> assignments = new HashMap<Initiator, List<StoragePort>>();
            Map<Initiator, List<StoragePort>> existingAssignments =
                    generateInitiatorsToStoragePortsMap(existingZoningMap, virtualArrayUri);
            if (!newInitiators.isEmpty()) {
                // discover existing zones that are for the storage system and varray
                // At this time we are not discovering routed zones but we will take care of this
                Collection<StoragePort> ports = ExportUtils.getStorageSystemAssignablePorts(
                        _dbClient, storage.getId(), virtualArrayUri, pathParams);
                Map<NetworkLite, List<Initiator>> initiatorsByNetwork = NetworkUtil.getInitiatorsByNetwork(newInitiators, _dbClient);
                Map<NetworkLite, List<StoragePort>> portByNetwork = ExportUtils.mapStoragePortsToNetworks(ports,
                        initiatorsByNetwork.keySet(), _dbClient);
                Map<NetworkLite, StringSetMap> zonesByNetwork = new HashMap<NetworkLite, StringSetMap>();

                // get all the prezoned ports for the initiators
                Map<NetworkLite, List<StoragePort>> preZonedPortsByNetwork =
                        getPrezonedPortsForInitiators(networkDeviceController, portByNetwork, initiatorsByNetwork, zonesByNetwork, token);

                if (!preZonedPortsByNetwork.isEmpty()) {
                    // trim the initiators to the pre-zoned ports
                    StringMapUtil.retainAll(initiatorsByNetwork, preZonedPortsByNetwork);
                    Map<NetworkLite, List<StoragePort>> allocatedPortsByNetwork = allocatePorts(
                            storage, virtualArrayUri, initiatorsByNetwork,
                            preZonedPortsByNetwork, volumeURIs, prezoningPathParams, existingZoningMap);
                    // Compute the number of Ports needed for each Network
                    StoragePortsAssigner assigner = StoragePortsAssignerFactory
                            .getAssignerForZones(storage.getSystemType(), zonesByNetwork);

                    for (NetworkLite network : allocatedPortsByNetwork.keySet()) {
                        // Assign the Storage Ports.
                        assigner.assign(assignments, initiatorsByNetwork.get(network),
                                allocatedPortsByNetwork.get(network), prezoningPathParams,
                                existingAssignments, network);
                    }
                    addAssignmentsToZoningMap(assignments, newZoningMap);
                }
                // if manual zoning is on, then make sure the paths discovered meet the path requirement
                if (allocateFromPrezonedPortsOnly(virtualArrayUri, storage.getSystemType(), backend)) {
                    try {
                        validateMinPaths(storage, prezoningPathParams, existingAssignments, assignments,
                                newInitiators);
                    } catch (PlacementException pex) {
                        _log.error("There are fewer pre-zoned paths than required by the virtual pool."
                                + " Please either add the needed paths or enable automatic SAN zoning in the virtual array"
                                + " so that the additional paths can be added by the application.", pex);
                        throw pex;
                    }
                }
            }
            _log.info("Zoning map after the assignment of pre-zoned ports: ", newZoningMap);
        } catch (Exception ex) {
            _log.error("Failed to assign from pre-zoned storage ports because: ", ex);
            if (allocateFromPrezonedPortsOnly(virtualArrayUri, storage.getSystemType(), backend)) {
                _log.error("The virtual array is configured for manual zoning and the application "
                        + "cannot assign from other storage ports. Failing the workflow.");
                throw ex;
            } else {
                _log.info("The virtual array is configured for auto zoning and the application "
                        + "will attempt to assign from other storage ports. Resuming the workflow.");
            }
        }
        return newZoningMap;
    }

    /**
     * This function changes the exportPaths parameter to be used when allocating from pre-zoned ports. When additional
     * paths can be added to the pre-zoned ones, the pre-zoned ports allocation need not verify paths requirements.
     * When additional paths cannot be added, then pre-zoned ports allocation need not verify paths requirements.
     * This function ensures the proper paths parameters are passed to pre-zoned ports allocation.
     * 
     * @param virtualArrayUri -- the export virtual array
     * @param exportPathParams -- the required paths
     * @param storage TODO
     * @param backend TODO
     * @return the paths parameter for allocating pre-zoned ports.
     */
    private ExportPathParams getPrezoningPathParam(URI virtualArrayUri,
            ExportPathParams exportPathParams, StorageSystem storage, boolean backend) {
        ExportPathParams prezoningPathParams = new ExportPathParams(exportPathParams.getMaxPaths(),
                exportPathParams.getMinPaths(), exportPathParams.getPathsPerInitiator(), exportPathParams.returnExportGroupType());
        if (!allocateFromPrezonedPortsOnly(virtualArrayUri, null, false)) {
            // If the application is expected to supply missing paths, then the export path needs to be
            // changed to avoid failure on minPath check when prezoned paths do not meet the requirements
            prezoningPathParams.setMinPaths(0);
            prezoningPathParams.setAllowFewerPorts(true);
        }
        return prezoningPathParams;
    }

    /**
     * When the configuration is for pre-zoned ports to be use and, at the same time, auto-zoning is off, additional
     * paths will not be added. In this case we need to ensure the pre-zoned paths meet the required paths. This
     * check indicates if additional paths will or will not be added.
     * 
     * @param virtualArrayUri -- the export virtual array
     * @param storageSystemType the type storage system of the ports
     * @param backend TODO
     * 
     * @return true if additional paths can be added to pre-zoned ones
     */
    private boolean allocateFromPrezonedPortsOnly(URI virtualArrayUri, String storageSystemType, boolean backend) {
        return _networkScheduler.portAllocationUseExistingZones(storageSystemType, backend)
                && !NetworkScheduler.isZoningRequired(_dbClient, virtualArrayUri);
    }

    /**
     * Return the VirtualPool URI of the blockObject.
     * 
     * @param blockObject
     * @param _dbClient
     * @return
     */
    private URI getBlockObjectVPoolURI(BlockObject blockObject, DbClient _dbClient) {
        Volume volume = null;
        URI vPoolURI = null;
        if (blockObject instanceof BlockSnapshot) {
            BlockSnapshot snap = (BlockSnapshot) blockObject;
            if (!NullColumnValueGetter.isNullNamedURI(snap.getParent())) {
                volume = _dbClient.queryObject(Volume.class, snap.getParent().getURI());
                vPoolURI = volume.getVirtualPool();
            }
        } else if (blockObject instanceof Volume) {
            volume = (Volume) blockObject;
            vPoolURI = volume.getVirtualPool();
        }
        return vPoolURI;
    }

    /**
     * Creates a map of initiators grouped and keyed by their network.
     * Initiators which are not in any network are not returned. Initiators
     * in zoningMap are not returned.
     * 
     * @param initiators the initiators
     * @param client
     * @return a map of network-to-initiators
     */
    public Map<NetworkLite, List<Initiator>> getNewInitiatorsByNetwork(Collection<Initiator> initiators,
            StringSetMap zoninMap, DbClient dbClient) {
        Map<NetworkLite, List<Initiator>> map = new HashMap<NetworkLite, List<Initiator>>();
        NetworkLite network = null;
        for (Initiator initiator : initiators) {
            network = NetworkUtil.getEndpointNetworkLite(initiator.getInitiatorPort(), dbClient);
            if (network == null) {
                _log.info(String.format("Initiator %s (%s) is being removed from initiator list because it has no network association",
                        initiator.getInitiatorPort(), initiator.getHostName()));
                continue;
            }
            if (zoninMap != null && zoninMap.containsKey(initiator.getId().toString())) {
                _log.info(String.format("Initiator %s (%s) is being removed from initiator list because it already exists in zoning map",
                        initiator.getInitiatorPort(), initiator.getHostName()));
                continue;
            }
            StringMapUtil.addToListMap(map, network, initiator);
        }
        return map;
    }

    /**
     * Reads the existing zones for the initiators from the network system and finds all ports that are
     * already prezoned to one or more of the initiators.
     * 
     * @param networkDeviceController an instance of networkDeviceController
     * @param portByNetwork the ports in the export mask grouped by network
     * @param initiatorsByNetwork the initiators of interest grouped by network
     * @param zonesByNetwork an OUT param to collect the zones found grouped by network
     * @param token the workflow step id
     * @return a map of ports in networks that are already zoned to one or more of the initiators
     */
    public Map<NetworkLite, List<StoragePort>> getPrezonedPortsForInitiators(NetworkDeviceController networkDeviceController,
            Map<NetworkLite, List<StoragePort>> portByNetwork, Map<NetworkLite, List<Initiator>> initiatorsByNetwork,
            Map<NetworkLite, StringSetMap> zonesByNetwork, String token) {
        // so now we have a a collection of initiators and ports, let's get the zones
        Map<NetworkLite, List<StoragePort>> preZonedPortsByNetwork = new HashMap<NetworkLite, List<StoragePort>>();
        StringSetMap zonesInNetwork = null;
        Map<String, List<Zone>> initiatorWwnToZonesMap = new HashMap<String, List<Zone>>();
        for (NetworkLite network : portByNetwork.keySet()) {
            if (!Transport.FC.toString().equals(network.getTransportType())) {
                continue;
            }
            List<Initiator> networkInitiators = initiatorsByNetwork.get(network);
            if (networkInitiators == null || networkInitiators.isEmpty()) {
                continue;
            }
            Map<String, StoragePort> portByWwn = DataObjectUtils.mapByProperty(portByNetwork.get(network), "portNetworkId");
            zonesInNetwork = networkDeviceController.getZoningMap(network, networkInitiators,
                    portByWwn, initiatorWwnToZonesMap);
            _log.info("Existing zones in network {} are {}", network.getNativeGuid(), zonesInNetwork);

            // if the OUT parameter is not null, fill in the discovered zones
            if (zonesByNetwork != null && !zonesInNetwork.isEmpty()) {
                zonesByNetwork.put(network, zonesInNetwork);
            }
            for (String iniId : zonesInNetwork.keySet()) {
                for (String portId : zonesInNetwork.get(iniId)) {
                    StringMapUtil.addToListMap(preZonedPortsByNetwork,
                            network, DataObjectUtils.findInCollection(portByNetwork.get(network), URI.create(portId)));
                }
            }
        }

        // now store the retrieved zones in ZK
        if (!initiatorWwnToZonesMap.isEmpty()) {
            Map<String, List<Zone>> zonesMap = (Map<String, List<Zone>>) WorkflowService.getInstance().loadWorkflowData(token, "zonemap");
            // some workflows call port allocation more than one time, rather than overriding, add to these zones to already stored zones.
            if (zonesMap == null) {
                zonesMap = initiatorWwnToZonesMap;
            } else {
                zonesMap.putAll(initiatorWwnToZonesMap);
            }
            WorkflowService.getInstance().storeWorkflowData(token, "zonemap", zonesMap);
        }
        return preZonedPortsByNetwork;
    }
}
