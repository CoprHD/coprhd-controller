/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAssigner;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;

/**
 * This implementation is used for export operations that are only intended to be
 * used for VPlex multiple masking. These interfaces pass lower level operations
 * reading the Export Masks to the VPlexBackendManager.
 * 
 * There are also operations here for the generation of Masking Views. These are:
 * getPortGroups(), getInitiatorGroups(), and configureZoning().
 * 
 */
public class VPlexHDSMaskingOrchestrator extends HDSMaskingOrchestrator
        implements VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VPlexHDSMaskingOrchestrator.class);
    private boolean simulation = false;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VPlexHDSMaskingOrchestrator() {
    }

    public VPlexHDSMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    @Override
    public Map<URI, ExportMask> readExistingExportMasks(
            StorageSystem storage,
            BlockStorageDevice device, List<Initiator> initiators) {
        return super.readExistingExportMasks(storage, device, initiators);
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage,
            BlockStorageDevice device, ExportMask mask) {
        return super.refreshExportMask(storage, device, mask);
    }

    @Override
    public void suggestExportMasksForPlacement(
            StorageSystem storage, BlockStorageDevice device, List<Initiator> initiators,
            ExportMaskPlacementDescriptor placementDescriptor) {
        super.suggestExportMasksForPlacement(storage, device, initiators, placementDescriptor);
    }

    static final Integer MAX_PORTS_PER_NETWORK = 24;

    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(
            Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap, URI varrayURI, int nInitiatorGroups) {

        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();

        // Group the networks into sets based on the number of ports they have.
        StringSet netNames = new StringSet();
        Map<Integer, Set<URI>> numPortsToNetworkSet = new HashMap<Integer, Set<URI>>();
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts > MAX_PORTS_PER_NETWORK) {
                numPorts = MAX_PORTS_PER_NETWORK;
            }
            if (numPortsToNetworkSet.get(numPorts) == null) {
                numPortsToNetworkSet.put(Integer.valueOf(numPorts), new HashSet<URI>());
            }
            numPortsToNetworkSet.get(Integer.valueOf(numPorts)).add(networkURI);
            netNames.add(networkMap.get(networkURI).getLabel());
        }
        _log.info("Calculating PortGroups for Networks %s: " + netNames.toString());

        // Eliminate ports from the same cpus, which are in the same portGroup.
        // This is to avoid the 4096 LUN limit per cpu.
        // Start with networks with fewest ports and work up. THis is because
        // in the case of duplicate us of a cpu by two ports, we want to eliminate
        // the port in the Network containing the most ports. So by starting with
        // the Network with the fewest ports to populate the cpusUsed set, we will
        // end up eliminating the port that is in the Network with a higher number of
        // ports.
        Set<String> cpusUsed = new HashSet<String>();
        for (Integer numPorts = 1; numPorts < MAX_PORTS_PER_NETWORK; numPorts++) {
            Set<URI> networkURIs = numPortsToNetworkSet.get(numPorts);
            if (networkURIs == null) {
                continue;
            }
            for (URI networkURI : networkURIs) {
                List<StoragePort> nonConflictedPorts = new ArrayList<StoragePort>();
                for (StoragePort port : allocatablePorts.get(networkURI)) {
                    if (!cpusUsed.contains(port.getPortGroup())) {
                        cpusUsed.add(port.getPortGroup());
                        nonConflictedPorts.add(port);
                    } else {
                        _log.info(String.format("Eliminating port %s because cpu already used", port.getPortName()));
                    }
                }
                allocatablePorts.put(networkURI, nonConflictedPorts);
            }
        }
        // Determine the network with the lowest number of allocatable ports.
        int minPorts = Integer.MAX_VALUE;
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts > MAX_PORTS_PER_NETWORK) {
                numPorts = MAX_PORTS_PER_NETWORK;
            }
            if (numPorts < minPorts) {
                minPorts = numPorts;
            }
        }

        // Figure out the number of ports in each network per port group (PG).
        // Then figure out the number of port groups to be generated.
        // HEURISTIC:
        // 1-3 ports, use 1 port per MV, unless there's only one Network, then use 2.
        // If it has 8 or more ports, use 2 ports per network, 4 for MV.
        // If it has 18 or more ports, use 3 ports per network, 6 per MV.
        // oneNetwork indicates if there is only one Network available.
        // portsPerPG is the number of ports to be allocated per PortGroup from the
        // network with the fewest ports.
        // numPG is the number of Port Groups that will be configured.
        boolean oneNetwork = allocatablePorts.keySet().size() == 1;
        int portsPerPG = oneNetwork ? 2 : 1;
        if (minPorts >= 8) {
            portsPerPG = 2;
        }
        if (minPorts >= 18) {
            portsPerPG = 3;
        }
        int numPG = minPorts / portsPerPG;
        if (numPG == 0) {
            return portGroups;
        }
        _log.info(String.format("Number Port Groups %d Per Network Ports Per Group %d", numPG, portsPerPG));

        // Make a map per Network of number of ports to allocate.
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();
        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / numPG;
            // Don't allow this network to have more than twice the number of
            // ports from the network with the fewest ports, i.e. do not exceed 2x portsPerPG.
            if (nports > (2 * portsPerPG)) {
                nports = 2 * portsPerPG;
            }
            portsAllocatedPerNetwork.put(netURI, nports);
        }

        // Now call the StoragePortsAllocator for each Network, assigning required number of ports.

        StoragePortsAllocator allocator = new StoragePortsAllocator();
        for (int i = 0; i < numPG; i++) {
            Map<URI, List<List<StoragePort>>> portGroup = new HashMap<URI, List<List<StoragePort>>>();
            StringSet portNames = new StringSet();
            for (URI netURI : allocatablePorts.keySet()) {
                NetworkLite net = networkMap.get(netURI);
                List<StoragePort> allocatedPorts = allocatePorts(allocator, allocatablePorts.get(netURI),
                        portsAllocatedPerNetwork.get(netURI), net, varrayURI);
                if (portGroup.get(netURI) == null) {
                    portGroup.put(netURI, new ArrayList<List<StoragePort>>());
                }
                portGroup.get(netURI).add(allocatedPorts);
                allocatablePorts.get(netURI).removeAll(allocatedPorts);
                for (StoragePort port : allocatedPorts) {
                    portNames.add(port.getPortName());
                }
            }
            portGroups.add(portGroup);
            _log.info(String.format("Port Group %d: %s", i, portNames.toString()));
            // Reinitialize the context in the allocator; we want redundancy within PG
            allocator.getContext().reinitialize();
        }
        return portGroups;
    }

    /**
     * Allocates StoragePorts (in either the simulated or production modes).
     * 
     * @param candidatePorts -- List of ports from which to choose
     * @param portsRequested -- Integer number of ports requested
     * @param net -- NetworkLite network
     * @param varrayURI -- URI of VirtualArray
     * @return List of StoragePorts allocated.
     */
    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested,
            NetworkLite net, URI varrayURI) {
        Collections.shuffle(candidatePorts);
        if (simulation) {
            StoragePortsAllocator.PortAllocationContext context = StoragePortsAllocator.getPortAllocationContext(
                    net, "arrayX", allocator.getContext());
            for (StoragePort port : candidatePorts) {
                context.addPort(port, null, null, null, null);
            }
            List<StoragePort> portsAllocated =
                    allocator.allocatePortsForNetwork(portsRequested, context, false, null, false);
            allocator.setContext(context);
            return portsAllocated;
        } else {
            Map<StoragePort, Long> sportMap = _blockScheduler.computeStoragePortUsage(candidatePorts);
            List<StoragePort> portsAllocated = allocator.selectStoragePorts(_dbClient,
                    sportMap, net, varrayURI, portsRequested, null, false);
            return portsAllocated;
        }
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            Map<URI, NetworkLite> networkMap, StoragePortsAssigner assigner) {
        return VPlexBackEndOrchestratorUtil.configureZoning(portGroup, initiatorGroup, networkMap, assigner);
    }

    @Override
    public Workflow.Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumeMap, completer);
    }

    /**
     * Create an ExportMask on the HDS if it does not exist. Otherwise, just add the indicated
     * volumes to the ExportMask.
     */
    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, fail, ask user to retry.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s deleted or inactive, failing", exportMaskURI));
                ServiceError svcerr = VPlexApiException.errors
                        .createBackendExportMaskDeleted(exportMaskURI.toString(), arrayURI.toString());
                WorkflowStepCompleter.stepFailed(stepId, svcerr);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            exportMask = refreshExportMask(array, device, exportMask);
            if (!exportMask.hasAnyVolumes()) {
                // We are creating this ExportMask on the hardware! (Maybe not the first time though...)
                // Fetch the Initiators
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                    }
                }
                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }
                // Set the volumes to added to the exportMask.
                if (volumeMap != null) {
                    for (URI volume : volumeMap.keySet()) {
                        exportMask.addVolume(volume, volumeMap.get(volume));
                    }
                }
                device.doExportGroupCreate(array, exportMask, volumeMap,
                        initiators, targets, completer);
            } else {
                device.doExportAddVolumes(array, exportMask, volumeMap, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for hds: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }
    }

    @Override
    public Workflow.Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, completer);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer, String stepId) {
        try {
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, nothing to do.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s inactive, returning success", exportMaskURI));
                WorkflowStepCompleter.stepSucceded(stepId);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Make sure the completer will complete the workflow. This happens on rollback case.
            if (!completer.getOpId().equals(stepId)) {
                completer.setOpId(stepId);
            }

            // Refresh the ExportMask
            exportMask = refreshExportMask(array, device, exportMask);
            // Determine if we're deleting the last volume.
            Set<String> remainingVolumes = new HashSet<String>();
            if (exportMask.getVolumes() != null) {
                remainingVolumes.addAll(exportMask.getVolumes().keySet());
            }
            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
            }
            // If so, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && (exportMask.getExistingVolumes() == null || exportMask.getExistingVolumes().isEmpty())) {
                device.doExportGroupDelete(array, exportMask, completer);
            } else {
                device.doExportRemoveVolumes(array, exportMask, volumes, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for hds: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    @Override
    public void setWorkflowService(WorkflowService _workflowService) {
        this._workflowService = _workflowService;
    }
}
