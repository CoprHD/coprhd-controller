/*
 * Copyright (c) 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskOnlyRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator.PortAllocationContext;
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
 * @author watson
 */
public class VPlexVmaxMaskingOrchestrator extends VmaxMaskingOrchestrator
        implements VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VPlexVmaxMaskingOrchestrator.class);
    private boolean simulation = false;
    // Set morePortGroups to true for testing only if you want to generate additional port groups with
    // a small number of ports (e.g. two ports per network, which is typical in our Lab)
    // By default it will require two ports per Network for each Port Group which will generate fewer PortGroups
    private static boolean morePortGroups = false;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VPlexVmaxMaskingOrchestrator() {
    }

    public VPlexVmaxMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
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
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(StorageSystem array,
            Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap, URI varrayURI, int nInitiatorGroups, 
            Map<URI, Map<String, Integer>> switchToPortNumber,
            Map<URI, PortAllocationContext> contextMap, StringBuilder errorMessages) {

        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();

        StringSet netNames = new StringSet();
        // Order the networks from those with fewest ports to those with the most ports.
        List<URI> orderedNetworks = orderNetworksByNumberOfPorts(allocatablePorts);
        for (URI networkURI : orderedNetworks) {
            netNames.add(networkMap.get(networkURI).getLabel());
        }
        _log.info("Calculating PortGroups for Networks: " + netNames.toString());

        Set<String> cpusUsed = new HashSet<String>();
        Map<URI, List<StoragePort>> useablePorts = new HashMap<URI, List<StoragePort>>();
        Set<String> eliminatedPorts = new HashSet<String>();
        Set<String> usedPorts = new HashSet<String>();

        // Eliminate ports from the same cpus, which are in the same portGroup.
        // This is to avoid the 4096 LUN limit per cpu.
        // Cycle through the networks, picking ports that can be used while considering cpus.
        // Pick one port from each network, then cycle through them again.
        boolean portWasPicked;
        do {
            portWasPicked = false;
            for (URI networkURI : orderedNetworks) {
                if (!useablePorts.containsKey(networkURI)) {
                    useablePorts.put(networkURI, new ArrayList<StoragePort>());
                }
                // Pick a port if possible
                for (StoragePort port : allocatablePorts.get(networkURI)) {
                    // Do not choose a port that has already been chosen
                    if (usedPorts.contains(port.getPortName())) {
                        continue;
                    }
                    if (array == null || array.checkIfVmax3() || !cpusUsed.contains(port.getPortGroup())) {
                        // Choose this port, it has a new cpu or is VMAX3.
                        cpusUsed.add(port.getPortGroup());
                        usedPorts.add(port.getPortName());
                        useablePorts.get(networkURI).add(port);
                        portWasPicked = true;
                        break;
                    } else {
                        // NOT VMAX3: This port shares a cpu, don't choose it.
                        eliminatedPorts.add(port.getPortName());
                    }
                }
            }
        } while (portWasPicked);

        // If all networks have some ports remaining, use the filtered ports.
        // If not, emit a warning and do not use the filtered port configuration.
        boolean useFilteredPorts = true;
        for (URI networkURI : orderedNetworks) {
            if (useablePorts.get(networkURI).isEmpty()) {
                useFilteredPorts = false;
                break;
            }
        }
        if (useFilteredPorts) {
            String message = String.format(
                    "Ports successfully selected are %s. Ports eliminated because of sharing a cpu with an already-selected port are %s.",
                    usedPorts.toString(), eliminatedPorts.toString());
            _log.info(message);
            if (errorMessages != null) {
                errorMessages.append(message);
            }

            allocatablePorts = useablePorts;
        } else {
            _log.info("Some networks have zero remaining ports after cpu filtering, will use duplicate ports on some cpus. "
                    + "This is not a recommended configuration.");
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
        // If the smallest network has 1 port, then allow 1 port per Network unless there's only 1 Network.
        // If there is only one network, require two ports per network in the Port Group.
        // If it has 2 or more ports per network, use 2 ports per network per MV.
        // If there are one or two networks, if it has 9 or more ports, use 3 ports per network per MV.
        // If there are one or two networks, if it has 16 or more ports, use 4 ports per network per MV.
        // oneNetwork indicates if there is only one Network available.
        // portsPerPG is the number of ports to be allocated per PortGroup from the
        // network with the fewest ports.
        // numPG is the number of Port Groups that will be configured.
        boolean oneNetwork = allocatablePorts.keySet().size() == 1;
        boolean moreThanTwoNetworks = allocatablePorts.keySet().size() > 2;
        int portsPerNetPerPG = oneNetwork ? 2 : 1;
        // It is best practice if each network has at least two ports, favoring fewer port groups.
        // But if "morePortGroups" is set, will make additional portGroups if there are fewer ports.
        if (morePortGroups) { // This can be set true for testing environments
            if (minPorts >= 4) {
                portsPerNetPerPG = 2; // Makes at least two Port Groups if there are two ports
            }
        } else {
            if (minPorts >= 2) {
                portsPerNetPerPG = 2; // Default is to require at least two ports per Port Group
            }
        }
        if (!moreThanTwoNetworks) {
            if (minPorts >= 9) {
                portsPerNetPerPG = 3;
            }
            if (minPorts >= 16) {
                portsPerNetPerPG = 4;
            }
        }
        int numPG = minPorts / portsPerNetPerPG;
        String message = String.format("Min Ports: %d. Number Port Groups: %d. Ports Per Network Per Port Group: %d.", 
                minPorts, numPG, portsPerNetPerPG);
        _log.info(message);
        if (errorMessages != null) {
            errorMessages.append(message);
        }
        if (numPG == 0) {
            return portGroups;
        }

        // Make a map per Network of number of ports to allocate.
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();
        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / numPG;
            // Don't allow this network to have more than twice the number of
            // ports from the network with the fewest ports, i.e. do not exceed 2x portsPerPG.
            if (nports > (2 * portsPerNetPerPG)) {
                nports = 2 * portsPerNetPerPG;
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
                Map<String, Integer> switchCountMap = null;
                if (switchToPortNumber != null) {
                    switchCountMap = switchToPortNumber.get(netURI);
                }
                PortAllocationContext context = null;
                if (contextMap != null) {
                    context = contextMap.get(netURI);
                }
                List<StoragePort> allocatedPorts = allocatePorts(allocator, allocatablePorts.get(netURI),
                        portsAllocatedPerNetwork.get(netURI), net, varrayURI, switchCountMap, context);
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
            if (allocator.getContext() != null) {
                allocator.getContext().reinitialize();
            }
        }
        return portGroups;
    }

    /**
     * Order the networks from those with least ports to those with most ports
     * 
     * @param allocatablePorts
     *            -- Map of Network URI to list of ports
     * @return ordered list of Network URIs
     */
    private List<URI> orderNetworksByNumberOfPorts(Map<URI, List<StoragePort>> allocatablePorts) {
        List<URI> orderedNetworks = new ArrayList<URI>();

        Map<Integer, Set<URI>> numPortsToNetworkSet = new HashMap<Integer, Set<URI>>();
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts > MAX_PORTS_PER_NETWORK) {
                numPorts = MAX_PORTS_PER_NETWORK;
            }
            if (numPortsToNetworkSet.get(numPorts) == null) {
                numPortsToNetworkSet.put(numPorts, new HashSet<URI>());
            }
            numPortsToNetworkSet.get(numPorts).add(networkURI);
        }

        for (Integer numPorts = 1; numPorts <= MAX_PORTS_PER_NETWORK; numPorts++) {
            Set<URI> networkURIs = numPortsToNetworkSet.get(numPorts);
            if (networkURIs == null) {
                continue;
            }
            for (URI networkURI : networkURIs) {
                orderedNetworks.add(networkURI);
            }
        }
        return orderedNetworks;
    }

    /**
     * Allocates StoragePorts (in either the simulated or production modes).
     * 
     * @param candidatePorts
     *            -- List of ports from which to choose
     * @param portsRequested
     *            -- Integer number of ports requested
     * @param net
     *            -- NetworkLite network
     * @param varrayURI
     *            -- URI of VirtualArray
     * @return List of StoragePorts allocated.
     */
    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested,
            NetworkLite net, URI varrayURI, Map<String, Integer> switchToPortNumber,
            PortAllocationContext context) {
        Collections.shuffle(candidatePorts);
        if (simulation) {
            if (context == null) {
                context = StoragePortsAllocator.getPortAllocationContext(
                        net, "arrayX", allocator.getContext());
                for (StoragePort port : candidatePorts) {
                    context.addPort(port, null, null, null, null);
                }
            }
            List<StoragePort> portsAllocated = allocator.allocatePortsForNetwork(portsRequested, context, false, null, false, switchToPortNumber);
            allocator.setContext(context);
            return portsAllocated;
        } else {
            Map<StoragePort, Long> sportMap = _blockScheduler.computeStoragePortUsage(candidatePorts);
            List<StoragePort> portsAllocated = allocator.selectStoragePorts(_dbClient,
                    sportMap, net, varrayURI, portsRequested, null, false, switchToPortNumber);
            return portsAllocated;
        }
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            Map<URI, NetworkLite> networkMap, StoragePortsAssigner assigner,
            Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsMap,
            Map<URI, String> portSwitchMap) {
        return VPlexBackEndOrchestratorUtil.configureZoning(portGroup, initiatorGroup, networkMap, assigner, 
                initiatorSwitchMap, switchStoragePortsMap, portSwitchMap);
    }

    @Override
    public Workflow.Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask",
                arrayURI, exportGroupURI, exportMaskURI, volumeMap, initiatorURIs, completer);
    }

    /**
     * Create an ExportMask on the VMAX if it does not exist. Otherwise, just add the indicated
     * volumes to the ExportMask.
     */
    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs2, TaskCompleter completer, String stepId) {
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

            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_009);
            if (!exportMask.hasAnyVolumes()) {
                // We are creating this ExportMask on the hardware! (Maybe not the first time though...)
                // Fetch the Initiators
                List<URI> initiatorURIs = new ArrayList<URI>();
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                        initiatorURIs.add(initiator.getId());
                    }
                }
                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }
                // The default completer passed in is for add volume, create correct one
                completer = new ExportMaskCreateCompleter(exportGroupURI, exportMaskURI,
                        initiatorURIs, volumeMap, stepId);
                // Note that there should not be any code after the call to the device as the completer
                // will be invoked causing the workflow to continue execution with the next step.
                device.doExportCreate(array, exportMask, volumeMap,
                        initiators, targets, completer);
            } else {
                List<URI> initiatorURIs = new ArrayList<URI>();
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class, URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                        initiatorURIs.add(initiator.getId());
                    }
                }

                // Note that there should not be any code after the call to the device as the completer
                // will be invoked causing the workflow to continue execution with the next step.
                device.doExportAddVolumes(array, exportMask, initiators, volumeMap, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }
    }

    @Override
    public Workflow.Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, List<URI> initiatorURIs) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, initiatorURIs);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, List<URI> initiatorURIs, String stepId) {
        ExportTaskCompleter completer = null;
        try {
            completer = new ExportMaskOnlyRemoveVolumeCompleter(exportGroupURI,
                    exportMaskURI, volumes, stepId);
            WorkflowStepCompleter.stepExecuting(stepId);
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);

            // If the exportMask isn't found, or has been deleted, nothing to do.
            if (exportMask == null || exportMask.getInactive()) {
                _log.info(String.format("ExportMask %s inactive, returning success", exportMaskURI));
                completer.ready(_dbClient);
                return;
            }

            // Protect concurrent operations by locking {host, array} dupple.
            // Lock will be released when workflow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys, LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            exportMask = refreshExportMask(array, device, exportMask);

            // Determine if we're deleting the last volume in the mask.
            StringMap maskVolumesMap = exportMask.getVolumes();
            Set<String> remainingVolumes = new HashSet<String>();
            List<URI> passedVolumesInMask = new ArrayList<>(volumes);
            if (maskVolumesMap != null) {
                remainingVolumes.addAll(maskVolumesMap.keySet());
            }
            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
                
                // Remove any volumes from the volume list that are no longer
                // in the export mask. When a failure occurs removing a backend
                // volume from a mask, the rollback method will try and remove it
                // again. However, in the case of a distributed volume, one side
                // may have succeeded, so we will try and remove it again. Previously,
                // this was not a problem. However, new validation exists at the
                // block level that checks to make sure the volume to remove is
                // actually in the mask, which now causes a failure when you remove
                // it a second time. So, we check here and remove any volumes that
                // are not in the mask to handle this condition.
                if ((maskVolumesMap != null) && (!maskVolumesMap.keySet().contains(volume.toString()))){
                    passedVolumesInMask.remove(volume);
                }
            }
            
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_043);
            // None of the volumes is in the export mask, so we are done.
            if (passedVolumesInMask.isEmpty()) {
                _log.info("None of these volumes {} are in export mask {}", volumes, exportMask.forDisplay());
                completer.ready(_dbClient);
                return;
            }
            
            // If it is last volume and there are no existing volumes, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && !exportMask.hasAnyExistingVolumes()) {
                device.doExportDelete(array, exportMask, passedVolumesInMask, initiatorURIs, completer);
            } else {
                List<Initiator> initiators = null;
                if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                    initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
                }
                device.doExportRemoveVolumes(array, exportMask, passedVolumesInMask, initiators, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            completer.error(_dbClient, vplexex);
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
