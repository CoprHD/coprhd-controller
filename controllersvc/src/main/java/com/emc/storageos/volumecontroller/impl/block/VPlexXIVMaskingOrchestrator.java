/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
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
import com.emc.storageos.workflow.Workflow.Method;
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
 * @author hallup
 *
 */
public class VPlexXIVMaskingOrchestrator extends XIVMaskingOrchestrator
        implements VplexBackEndMaskingOrchestrator, Controller {

    private static final Logger _log = LoggerFactory.getLogger(VPlexXIVMaskingOrchestrator.class);
    private static final int XIV_NUM_PORT_GROUP = 1;
    private BlockDeviceController _blockController = null;
    private WorkflowService workflowService = null;
    private boolean simulation = false;

    public VPlexXIVMaskingOrchestrator() {

    }

    public VPlexXIVMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public WorkflowService getWorkflowService() {
        return workflowService;
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    @Override
    public void setWorkflowService(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Override
    public Map<URI, ExportMask> readExistingExportMasks(
            StorageSystem storage,
            BlockStorageDevice device,
            List<Initiator> initiators) {
        return super.readExistingExportMasks(storage, device, initiators);
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage,
            BlockStorageDevice device, ExportMask mask) {
        return super.refreshExportMask(storage, device, mask);
    }

    @Override
    public void suggestExportMasksForPlacement(
            StorageSystem storage, BlockStorageDevice device,
            List<Initiator> initiators,
            ExportMaskPlacementDescriptor placementDescriptor) {
        super.suggestExportMasksForPlacement(storage, device, initiators, placementDescriptor);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#getPortGroups(java.util.Map, java.util.Map, java.net.URI, int)
     */
    @Override
    public Set<Map<URI, List<List<StoragePort>>>> getPortGroups(StorageSystem array,
            Map<URI, List<StoragePort>> allocatablePorts,
            Map<URI, NetworkLite> networkMap,
            URI varrayURI, int nInitiatorGroups,
            Map<URI, Map<String, Integer>> switchToPortNumber, 
            Map<URI, PortAllocationContext> contextMap, StringBuilder errorMessages) {

        _log.debug("START - getPortGroups");
        Set<Map<URI, List<List<StoragePort>>>> portGroups = new HashSet<Map<URI, List<List<StoragePort>>>>();
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();

        // Port Group is always 1 for XIV as of now.
        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / XIV_NUM_PORT_GROUP;
            portsAllocatedPerNetwork.put(netURI, nports);
        }

        StoragePortsAllocator allocator = new StoragePortsAllocator();

        for (int i = 0; i < XIV_NUM_PORT_GROUP; i++) {
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
                List<StoragePort> allocatedPorts = allocatePorts(allocator,
                        allocatablePorts.get(netURI),
                        portsAllocatedPerNetwork.get(netURI),
                        net,
                        varrayURI,
                        switchCountMap,
                        context);
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
            _log.info(String.format("Port Group %d: port names in the port group {%s}", i, portNames.toString()));

        }

        _log.debug("END - getPortGroups");
        return portGroups;
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#configureZoning(java.util.Map, java.util.Map, java.util.Map,
     * com.emc.storageos.volumecontroller.placement.StoragePortsAssigner)
     */
    @Override
    public StringSetMap configureZoning(Map<URI, List<List<StoragePort>>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup,
            Map<URI, NetworkLite> networkMap,
            StoragePortsAssigner assigner,
            Map<URI, String> initiatorSwitchMap,
            Map<URI, Map<String, List<StoragePort>>> switchStoragePortsMap,
            Map<URI, String> portSwitchMap) {

        return VPlexBackEndOrchestratorUtil.configureZoning(portGroup, initiatorGroup, networkMap, assigner,
                initiatorSwitchMap, switchStoragePortsMap, portSwitchMap);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#createOrAddVolumesToExportMaskMethod(java.net.URI, java.net.URI,
     * java.net.URI, java.util.Map, com.emc.storageos.volumecontroller.TaskCompleter)
     */
    @Override
    public Method createOrAddVolumesToExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs, TaskCompleter completer) {

        return new Workflow.Method("createOrAddVolumesToExportMask",
                arrayURI, exportGroupURI, exportMaskURI, volumeMap, initiatorURIs, completer);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#createOrAddVolumesToExportMask(java.net.URI, java.net.URI,
     * java.net.URI, java.util.Map, com.emc.storageos.volumecontroller.TaskCompleter, java.lang.String)
     */
    @Override
    public void createOrAddVolumesToExportMask(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs2,
            TaskCompleter completer, String stepId) {

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
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys,
                    LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            BlockStorageDevice device = _blockController.getDevice(array.getSystemType());
            if (exportMask.getNativeId() != null) {
                exportMask = refreshExportMask(array, device, exportMask);
            }
            
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
            
            if (!exportMask.hasAnyVolumes()) {
                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }

                //If some invalid export mask exists
                if (exportMask.getNativeId() != null) {
                    exportMask.setNativeId("");
                    _dbClient.updateAndReindexObject(exportMask);
                }

                // The default completer passed in is for add volume, create correct one
                TaskCompleter createCompleter = new ExportMaskCreateCompleter(exportGroupURI, exportMaskURI,
                        initiatorURIs, volumeMap, stepId);
                device.doExportCreate(array, exportMask, volumeMap,
                        initiators, targets, createCompleter);
            } else {
                device.doExportAddVolumes(array, exportMask, initiators, volumeMap, completer);
            }

        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for XIV: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#deleteOrRemoveVolumesFromExportMaskMethod(java.net.URI,
     * java.net.URI, java.net.URI, java.util.List)
     */
    @Override
    public Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, List<URI> initiatorURIs) {

        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, initiatorURIs);
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.volumecontroller.impl.block.
     * VplexBackEndMaskingOrchestrator#deleteOrRemoveVolumesFromExportMask(java.net.URI,
     * java.net.URI, java.net.URI, java.util.List,
     * java.lang.String)
     */
    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, List<URI> initiatorURIs,
            String stepId) {
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

            // Protect concurrent operations by locking {host, array} duple.
            // Lock will be released when work-flow step completes.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroupType.Host,
                    StringSetUtil.stringSetToUriList(exportMask.getInitiators()), arrayURI);
            getWorkflowService().acquireWorkflowStepLocks(stepId, lockKeys,
                    LockTimeoutValue.get(LockType.VPLEX_BACKEND_EXPORT));

            // Refresh the ExportMask
            if (exportMask.getNativeId() != null) {
                exportMask = refreshExportMask(array, device, exportMask);
            }

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
            _log.error("Failed to delete or remove volumes to export mask for XIV: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex.addStepsForDeleteVolumesFailed(ex);
            completer.error(_dbClient, vplexex);
        }

    }

    /**
     * Allocate target storage ports based on the I/O load. Least I/O loaded
     * storage port/s would be selected.
     * 
     * @param allocator
     * @param candidatePorts
     * @param portsRequested
     * @param nwLite
     * @param varrayURI
     * @return
     */
    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts,
            int portsRequested,
            NetworkLite nwLite,
            URI varrayURI,
            Map<String, Integer> switchToPortNumber,
            PortAllocationContext context) {
        return VPlexBackEndOrchestratorUtil.allocatePorts(allocator,
                candidatePorts,
                portsRequested,
                nwLite,
                varrayURI,
                simulation,
                _blockScheduler,
                _dbClient,
                switchToPortNumber,
                context);
    }

}
