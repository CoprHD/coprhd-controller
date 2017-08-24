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
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
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
 * In the future, additional methods will be added here to generate the port groups,
 * initiator groups, etc. automatically for multiple mask support.
 * 
 * @author watson
 */
public class VPlexVnxMaskingOrchestrator extends VnxMaskingOrchestrator implements
        VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VPlexVnxMaskingOrchestrator.class);
    private boolean simulation = false;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;
    
    private static final String  SPA= "SP_A";
    private static final String  SPB= "SP_B";
    

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VPlexVnxMaskingOrchestrator() {

    }

    public VPlexVnxMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
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

    /**
     * Returns the set of port groups that should be used.
     * Each port group is a map of Network to a list of Storage Ports in that Network.
     * Since at most we can construct two InitiatorGroups, we try to construct
     * two PortGroups.
     * 
     * @return Sets of PortGroups, where each Port Group is a map of Network URI
     *         to a List of Storage Ports.
     */
    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(
            StorageSystem array, Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap, URI varrayURI,
            int nInitiatorGroups, Map<URI, Map<String, Integer>> switchToPortNumber,
            Map<URI, PortAllocationContext> contextMap, StringBuilder errorMessages) {
        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();

        // Determine the network with the fewest ports. It will determine how many
        // port groups can be made.
        int minPorts = Integer.MAX_VALUE;
        for (URI networkURI : allocatablePorts.keySet()) {
            int numPorts = allocatablePorts.get(networkURI).size();
            if (numPorts < minPorts) {
                minPorts = numPorts;
            }
        }

        // Figure out the number of ports in each network per port group (PG).
        // Then figure out the number of port groups to be generated,
        // which will always be one or two.
        boolean oneNetwork = allocatablePorts.keySet().size() == 1;
        int numPG = 1;
        if (nInitiatorGroups == 2 && minPorts >= 2 && !oneNetwork) {
            numPG = 2;
        }
        if (numPG == 0) {
            return portGroups;
        }
        _log.info(String.format("Number Port Groups %d", numPG));

        // Make a map per Network of number of ports to allocate.
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();
        for (URI netURI : allocatablePorts.keySet()) {
            // Calculate the number of ports to be allocated for this net. It is:
            // the number of allocatable ports / numPG.
            Integer nports = allocatablePorts.get(netURI).size() / numPG;
            portsAllocatedPerNetwork.put(netURI, nports);
        }

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
     * @return
     */
    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested,
            NetworkLite net, URI varrayURI, Map<String, Integer> switchToPortNumber,
            PortAllocationContext context) {
        Collections.shuffle(candidatePorts);
        if (simulation) {
            if (context == null) {
                context = StoragePortsAllocator.getPortAllocationContext(net, "arrayX", allocator.getContext());
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
        StringSetMap zoningMap = new StringSetMap();

        // Set up a map to track port usage so that we can use all ports more or less equally.
        Map<StoragePort, Integer> portAUsage = new HashMap<StoragePort, Integer>();
        Map<StoragePort, Integer> portBUsage = new HashMap<StoragePort, Integer>();

        // check if switch affinity is on
        boolean isSwitchAffinity = false;
        if (initiatorSwitchMap != null && !initiatorSwitchMap.isEmpty() &&
                switchStoragePortsMap != null && !switchStoragePortsMap.isEmpty()) {
            isSwitchAffinity = true;
        }
        
        // Iterate through each of the directors, matching each of its initiators
        // with one port.
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
                    List<StoragePort> assignablePortsA = new ArrayList<StoragePort> ();
                    List<StoragePort> assignablePortsB = new ArrayList<StoragePort> ();
                    if (isSwitchAffinity) {
                        // find the ports with the same switch as the initiator
                        String switchName = initiatorSwitchMap.get(initiator.getId());
                        if (!switchName.equals(NullColumnValueGetter.getNullStr())) {
                            Map<String, List<StoragePort>>switchMap = switchStoragePortsMap.get(networkURI);
                            if (switchMap != null) {
                                List<StoragePort> switchPorts = switchMap.get(switchName);
                                if (switchPorts != null && !switchPorts.isEmpty()) {
                                    _log.info(String.format("Found the same switch ports, switch is %s", switchName));
                                    for (StoragePort port : switchPorts) {
                                        String groupId = port.getPortGroup();
                                        if (groupId != null && groupId.equals(SPA)) {
                                            assignablePortsA.add(port);
                                        } else if (groupId != null && groupId.equals(SPB)) {
                                            assignablePortsB.add(port);
                                        }
                                    }
                                    
                                }
                            }
                        }
                    }
                    StringSet ports = new StringSet();
                    // Get an A Port
                    String aPortName = " ", bPortName = " ";
                    if (assignablePortsA.isEmpty()) {
                        assignablePortsA = portGroup.get(networkURI).iterator().next();
                    }
                    StoragePort portA = VPlexBackEndOrchestratorUtil.assignPortToInitiator(
                            assigner, assignablePortsA, net, initiator, portAUsage, SPA);
                    if (portA != null) {
                        aPortName = portA.getPortName();
                        ports.add(portA.getId().toString());
                    }
                    // Get a B Port
                    if (assignablePortsB.isEmpty()) {
                        assignablePortsB = portGroup.get(networkURI).iterator().next();
                    }
                    StoragePort portB = VPlexBackEndOrchestratorUtil.assignPortToInitiator(
                            assigner, portGroup.get(networkURI).iterator().next(), net, initiator, portBUsage, SPB);
                    if (portB != null) {
                        bPortName = portB.getPortName();
                        ports.add(portB.getId().toString());
                    }
                    _log.info(String.format("%s %s   %s %s -> %s  %s %s %s",
                            director, net.getLabel(), initiator.getInitiatorPort(), initiatorSwitchMap.get(initiator.getId()), 
                            aPortName, portSwitchMap.get(portA.getId()), bPortName, portSwitchMap.get(portB.getId())));
                    zoningMap.put(initiator.getId().toString(), ports);
                }
            }
        }
        return zoningMap;
    }

    @Override
    public Workflow.Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask",
                arrayURI, exportGroupURI, exportMaskURI, volumeMap, initiatorURIs, completer);
    }

    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs2, TaskCompleter completer, String stepId) {
        try {
            StorageSystem array = _dbClient.queryObject(StorageSystem.class, arrayURI);
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            WorkflowStepCompleter.stepExecuting(stepId);

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

            // We do not refresh here, as the VNXExportOperations code will throw an exception
            // if the StorageGroup was not found.
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            if (!exportMask.hasAnyVolumes() && exportMask.getCreatedBySystem()) {
                // We are creating this ExportMask on the hardware! (Maybe not the first time though...)

                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }

                // Clear the export_mask nativeId; otherwise the VnxExportOps will attempt to look it
                // up and fail. An empty String will suffice as having no nativeId.
                if (exportMask.getNativeId() != null) {
                    exportMask.setNativeId("");
                    _dbClient.updateAndReindexObject(exportMask);
                }
                // The default completer passed in is for add volume, create correct one
                completer = new ExportMaskCreateCompleter(exportGroupURI, exportMaskURI,
                        initiatorURIs, volumeMap, stepId);
                device.doExportCreate(array, exportMask, volumeMap,
                        initiators, targets, completer);
            } else {
                device.doExportAddVolumes(array, exportMask, initiators, volumeMap, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for vnx: ", ex);
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
            completer.ready(_dbClient);
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for vnx: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            completer.error(_dbClient, vplexex);
        }
    }

    public boolean isSimulation() {
        return simulation;
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
