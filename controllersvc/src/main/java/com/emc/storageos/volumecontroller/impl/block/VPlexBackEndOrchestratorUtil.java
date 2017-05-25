/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;

public class VPlexBackEndOrchestratorUtil {
    private static final Logger _log = LoggerFactory.getLogger(VPlexBackEndOrchestratorUtil.class);

    public static final String DIRECTOR_MIN_PORT_COUNT_SETTING = "controller_vplex_director_min_port_count";

    public static List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested, NetworkLite net, URI varrayURI,
            boolean simulation, BlockStorageScheduler blockScheduler, DbClient dbClient,
            Map<String, Integer> switchToPortNumber, PortAllocationContext context) {
        Collections.shuffle(candidatePorts);
        if (simulation) {
            if (context == null) {
                context = StoragePortsAllocator.getPortAllocationContext(net, "arrayX", allocator.getContext());
                for (StoragePort port : candidatePorts) {
                    context.addPort(port, null, null, null, null);
                }
            }
            
            List<StoragePort> portsAllocated = allocator.allocatePortsForNetwork(portsRequested,
                    context, false, null, false, switchToPortNumber);
            allocator.setContext(context);
            return portsAllocated;
        } else {
            Map<StoragePort, Long> sportMap = blockScheduler
                    .computeStoragePortUsage(candidatePorts);
            List<StoragePort> portsAllocated = allocator.selectStoragePorts(dbClient, sportMap,
                    net, varrayURI, portsRequested, null, false, switchToPortNumber);
            return portsAllocated;
        }
    }

    public static StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup, Map<URI, NetworkLite> networkMap,
            StoragePortsAssigner assigner, Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsMap,
            Map<URI, String> portSwitchMap) {
        StringSetMap zoningMap = new StringSetMap();
        // Set up a map to track port usage so that we can use all ports more or less equally.
        Map<StoragePort, Integer> portUsage = new HashMap<StoragePort, Integer>();
        
        // check if switch affinity is on
        boolean isSwitchAffinity = false;
        if (initiatorSwitchMap != null && !initiatorSwitchMap.isEmpty() &&
                switchStoragePortsMap != null && !switchStoragePortsMap.isEmpty()) {
            isSwitchAffinity = true;
        }
        // Iterate through each of the directors, matching each of its initiators
        // with one port. This will ensure not to violate four paths per director.
        for (String director : initiatorGroup.keySet()) {
            for (URI networkURI : initiatorGroup.get(director).keySet()) {
                NetworkLite net = networkMap.get(networkURI);
                for (Initiator initiator : initiatorGroup.get(director).get(networkURI)) {
                    // If there are no ports on the initiators network, too bad...
                    if (portGroup.get(networkURI) == null) {
                        _log.info(String.format("%s -> no ports in network",
                                initiator.getInitiatorPort()));
                        continue;
                    }

                    // find a port for the initiator
                    List<StoragePort> assignablePorts = portGroup.get(networkURI).iterator().next();
                    if (isSwitchAffinity) {
                        // find the ports with the same switch as the initiator
                        String switchName = initiatorSwitchMap.get(initiator.getId());
                        if (!switchName.equals(NullColumnValueGetter.getNullStr())) {
                            Map<String, List<StoragePort>>switchMap = switchStoragePortsMap.get(networkURI);
                            if (switchMap != null) {
                                List<StoragePort> switchPorts = switchMap.get(switchName);
                                if (switchPorts != null && !switchPorts.isEmpty()) {
                                    _log.info(String.format("Found the same switch ports, switch is %s", switchName));
                                    assignablePorts = switchPorts;
                                } else {
                                    _log.info(String.format("Switch affinity is not honored, because no storage port from the switch %s for the initiator %s", 
                                            switchName, initiator.getInitiatorPort()));
                                }
                            }
                        }
                    }
                    StoragePort storagePort = assignPortToInitiator(assigner,
                            assignablePorts, net, initiator, portUsage, null);
                    if (storagePort != null) {
                        _log.info(String.format("%s %s   %s %s -> %s  %s %s", director, net.getLabel(),
                                initiator.getInitiatorPort(), initiatorSwitchMap.get(initiator.getId()),storagePort.getPortNetworkId(),
                                storagePort.getPortName(), portSwitchMap.get(storagePort.getId())));
                        StringSet ports = new StringSet();
                        ports.add(storagePort.getId().toString());
                        zoningMap.put(initiator.getId().toString(), ports);
                    } else {
                        _log.info(String.format("A port could not be assigned for %s %s   %s", director, net.getLabel(),
                                initiator.getInitiatorPort()));
                    }
                }
            }
        }
        return zoningMap;
    }

    /**
     * Validates that an ExportMask can be used.
     * There are comments for each rule that is validated below.
     * 
     * @param varrayURI the varray URI
     * @param initiatorPortMap map of Network to Vplex StoragePort list
     * @param mask the ExportMask to validate
     * @param invalidMasks a set of known invalidMask URIs
     * @param directorToInitiatorIds a map of directors to initiator port strings
     * @param idToInitiatorMap a map of initiator ports to Initiator objects
     * @param dbClient a reference to the database client
     * @param coordinator the system coordinator client
     * @param portWwnToClusterMap a map of port wwns to VPLEX cluster
     * @param errorMessages an error message builder
     * @return true if the given ExportMask can be used
     */
    public static boolean validateExportMask(URI varrayURI,
            Map<URI, List<StoragePort>> initiatorPortMap, ExportMask mask, Set<URI> invalidMasks,
            Map<String, Set<String>> directorToInitiatorIds, Map<String, Initiator> idToInitiatorMap,
            DbClient dbClient, CoordinatorClient coordinator, Map<String, String> portWwnToClusterMap, StringBuilder errorMessages) {

        boolean passed = true;
        Integer directorMinPortCount = Integer.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                coordinator, DIRECTOR_MIN_PORT_COUNT_SETTING));

        // Rule 1. An Export Mask must have at least two initiators from each director.
        // This is a warning if the ExportMask is non-ViPR.
        for (String director : directorToInitiatorIds.keySet()) {
            int portsInDirector = 0;
            for (String initiatorId : directorToInitiatorIds.get(director)) {
                Initiator initiator = idToInitiatorMap.get(initiatorId);
                String initiatorPortWwn = Initiator.normalizePort(initiator.getInitiatorPort());
                if (mask.hasExistingInitiator(initiatorPortWwn)) {
                    portsInDirector++;
                } else if (mask.hasUserInitiator(initiatorPortWwn)) {
                    portsInDirector++;
                } else if (mask.hasInitiator(initiatorId)) {
                    portsInDirector++;
                }
            }
            if (portsInDirector < directorMinPortCount) {
                if (mask.getCreatedBySystem()) {    // ViPR created
                    String msg = String.format(
                            "ExportMask %s disqualified because it only has %d back-end ports from %s (requires two). \n",
                            mask.getMaskName(), portsInDirector, director);
                    _log.info(msg);
                    if (errorMessages != null) {
                        errorMessages.append(msg);
                    }
                    if(null!=invalidMasks) {
                        invalidMasks.add(mask.getId());
                    }
                    
                    passed = false;
                } else {    // non ViPR created
                    String msg = String.format(
                            "ExportMask %s only has %d back-end ports from %s (should have at least two). \n",
                            mask.getMaskName(), portsInDirector, director);
                    _log.info(msg);
                    if (errorMessages != null) {
                        errorMessages.append(msg);
                    }
                }
            }
        }

        // Rule 2. The Export Mask should have at least two ports. Four are recommended.
        Set<String> usablePorts = new StringSet();
        if (mask.getStoragePorts() != null) {
            for (String portId : mask.getStoragePorts()) {
                StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portId));
                if (port == null || port.getInactive()
                        || NullColumnValueGetter.isNullURI(port.getNetwork())) {
                    continue;
                }
                // Validate port network overlaps Initiators and port is tagged for Varray
                StringSet taggedVarrays = port.getTaggedVirtualArrays();
                if (ConnectivityUtil.checkNetworkConnectedToAtLeastOneNetwork(port.getNetwork(), initiatorPortMap.keySet(), dbClient)
                        && taggedVarrays != null && taggedVarrays.contains(varrayURI.toString())) {
                    usablePorts.add(port.getLabel());
                }
            }
        }

        if (usablePorts.size() < 2) {
            String msg = String.format("ExportMask %s disqualified because it has less than two usable target ports;"
                    + " usable ports: %s \n", mask.getMaskName(), usablePorts.toString());
            _log.warn(msg);
            if (errorMessages != null) {
                errorMessages.append(msg);
            }
            passed = false;
        }
        else if (usablePorts.size() < 4) {  // This is a warning
            String msg = String.format("Warning: ExportMask %s has only %d usable target ports (best practice is at least four);"
                    + " usable ports: %s \n", mask.getMaskName(), usablePorts.size(), usablePorts.toString());
            _log.warn(msg);
            if (errorMessages != null) {
                errorMessages.append(msg);
            }
        }

        // Rule 3. No mixing of WWNs from both VPLEX clusters.
        // Add the clusters for all existingInitiators to the sets computed from initiators above.
        Set<String> clusters = new HashSet<String>();
        for (String portWwn : portWwnToClusterMap.keySet()) {
            if (mask.hasExistingInitiator(portWwn) || mask.hasUserInitiator(portWwn)) {
                clusters.add(portWwnToClusterMap.get(portWwn));
            }
        }
        if (clusters.size() > 1) {
            String msg = String.format("ExportMask %s disqualified because it contains wwns from both VPLEX clusters. \n",
                    mask.getMaskName());
            _log.warn(msg);
            if (errorMessages != null) {
                errorMessages.append(msg);
            }
            passed = false;
        }

        // Rule 4. The ExportMask name should not have NO_VIPR in it.
        if (mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
            String msg = String.format("ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it. \n",
                    mask.getMaskName(), ExportUtils.NO_VIPR);
            _log.warn(msg);
            if (errorMessages != null) {
                errorMessages.append(msg);
            }
            passed = false;
        }
        
        // Rule 5. Every port in the ExportMask must have the varray in its tagged varray set.
        StringBuilder portsNotInVarray = new StringBuilder();
        if (mask.getStoragePorts() != null) {
            for (String portId : mask.getStoragePorts()) {
                StoragePort port = dbClient.queryObject(StoragePort.class, URI.create(portId));
                if (port == null || port.getInactive()) {
                    continue;
                }
                // Validate port is tagged for Varray
                StringSet taggedVarrays = port.getTaggedVirtualArrays();
                if (taggedVarrays == null || taggedVarrays.isEmpty()
                        || !taggedVarrays.contains(varrayURI.toString())) {
                    portsNotInVarray.append(port.getPortName() + " ");
                }
            }
        }
        if (portsNotInVarray.length() > 0) {
            String virtualArrayName = varrayURI.toString();
            VirtualArray virtualArray = dbClient.queryObject(VirtualArray.class, varrayURI);
            if (virtualArray != null) {
                virtualArrayName = virtualArray.getLabel();
            }
            String msg = String.format("Validation of ExportMask %s failed; the mask has ports which are not in varray %s;\n" +
                    " \tPorts not in varray: %s \n", mask.getMaskName(), virtualArrayName, portsNotInVarray);
            _log.warn(msg);
            if (errorMessages != null) {
                errorMessages.append(msg);
            }
            passed = false;
        }

        int volumeCount = (mask.getVolumes() != null) ? mask.getVolumes().size() : 0;
        if (mask.getExistingVolumes() != null) {
            volumeCount += mask.getExistingVolumes().keySet().size();
        }
        if (passed) {
            _log.info(String.format("Validation of ExportMask %s passed; it has %d volumes",
                    mask.getMaskName(), volumeCount));
        } else {
            if (null != invalidMasks) {
                invalidMasks.add(mask.getId());
            }
        }
        return passed;
    }

    /**
     * This function tries to select a port for a given initiator and tries to minimize the number
     * of initiators using the same port. When the ports being assigned are pre-zoned, this
     * function also ensures that the port assigned to an initiator is pre-zoned to the initiator.
     * 
     * @param assigner -- the port assigner which ensures that when the ports being assigned are
     *            pre-zoned, this function also ensures that the port assigned to an initiator is pre-zoned
     *            to the initiator.
     * @param ports -- the list of ports to allocate from
     * @param net -- the initiator network
     * @param initiator -- the initiator
     * @param portUsage -- a IN/OUT parameter that tracks the port usage
     * @param groupId -- if the port must be in a given group like SP_A or SP_B. If null, this check not needed.
     * @return the storage port assigned to the initiator if one could be assigned. Null if a port
     *         could not be assigned.
     */
    public static StoragePort assignPortToInitiator(StoragePortsAssigner assigner, List<StoragePort> ports,
            NetworkLite net, Initiator initiator, Map<StoragePort, Integer> portUsage, String groupId) {
        StoragePort foundPort = null;
        for (StoragePort port : ports) {
            if (groupId == null || port.getPortGroup().equals(groupId)) {
                // make sure we have an entry in the map
                if (portUsage.get(port) == null) {
                    portUsage.put(port, 0);
                }
                if (assigner.isPortAssignableToInitiator(net, initiator, port)) {
                    if (foundPort == null) {
                        foundPort = port;
                    } else {
                        if (portUsage.get(port) < portUsage.get(foundPort)) {
                            foundPort = port;
                        }
                    }
                }
            }
        }
        if (foundPort != null) {
            portUsage.put(foundPort, portUsage.get(foundPort) + 1);
        }
        return foundPort;
    }
}
