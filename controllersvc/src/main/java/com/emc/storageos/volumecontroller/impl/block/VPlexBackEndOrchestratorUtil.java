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

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.util.ConnectivityUtil;
import com.emc.storageos.util.ExportUtils;
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
    
    /**
     * Validates that an ExportMask can be used.
     * There are comments for each rule that is validated below.
     * 
     * @param map of Network to Vplex StoragePort list.
     * @param mask
     * @param invalidMaskscontains
     * @param returns true if passed validation
     */
    public static boolean validateExportMask(URI varrayURI,
            Map<URI, List<StoragePort>> initiatorPortMap, ExportMask mask, Set<URI> invalidMasks,
            Map<String, Set<String>> directorToInitiatorIds, Map<String, Initiator> idToInitiatorMap,
            DbClient dbClient, Map<String, String> portWwnToClusterMap) {

        boolean passed = true;

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
                }
            }
            if (portsInDirector < 2) {
                if (mask.getCreatedBySystem()) {    // ViPR created
                    _log.info(String.format(
                            "ExportMask %s disqualified because it only has %d back-end ports from %s (requires two)",
                            mask.getMaskName(), portsInDirector, director));
                    invalidMasks.add(mask.getId());
                    passed = false;
                } else {    // non ViPR created
                    _log.info(String.format(
                            "Warning: ExportMask %s only has %d back-end ports from %s (should have at least two)",
                            mask.getMaskName(), portsInDirector, director));
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
            _log.info(String.format("ExportMask %s disqualified because it has less than two usable target ports;"
                    + " usable ports: %s", mask.getMaskName(), usablePorts.toString()));
            passed = false;
        }
        else if (usablePorts.size() < 4) {  // This is a warning
            _log.info(String.format("Warning: ExportMask %s has only %d usable target ports (best practice is at least four);"
                    + " usable ports: %s", mask.getMaskName(), usablePorts.size(), usablePorts.toString()));
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
            _log.info(String.format("ExportMask %s disqualified because it contains wwns from both Vplex clusters",
                    mask.getMaskName()));
            passed = false;
        }

        // Rule 4. The ExportMask name should not have NO_VIPR in it.
        if (mask.getMaskName().toUpperCase().contains(ExportUtils.NO_VIPR)) {
            _log.info(String.format("ExportMask %s disqualified because the name contains %s (in upper or lower case) to exclude it",
                    mask.getMaskName(), ExportUtils.NO_VIPR));
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
            invalidMasks.add(mask.getId());
        }
        return passed;
    }

}
