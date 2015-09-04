/*
 * Copyright 2015 EMC Corporation
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
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.ExportGroup.ExportGroupType;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerExceptions;
import com.emc.storageos.locking.DistributedOwnerLockService;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.placement.StoragePortsAllocator;
import com.emc.storageos.vplex.api.VPlexApiException;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.emc.storageos.workflow.Workflow.Method;

public class VplexXtremIOMaskingOrchestrator extends XtremIOMaskingOrchestrator implements
        VplexBackEndMaskingOrchestrator, Controller {
    private static final Logger _log = LoggerFactory.getLogger(VplexXtremIOMaskingOrchestrator.class);
    private boolean simulation = false;
    private int XTREMIO_NUM_PORT_GROUP = 1;
    BlockDeviceController _blockController = null;
    WorkflowService _workflowService = null;

    public VplexXtremIOMaskingOrchestrator() {
    }

    public void setBlockDeviceController(BlockDeviceController blockController) {
        this._blockController = blockController;
    }

    public VplexXtremIOMaskingOrchestrator(DbClient dbClient, BlockDeviceController controller) {
        this._dbClient = dbClient;
        this._blockController = controller;
    }

    @Override
    public Map<URI, ExportMask> readExistingExportMasks(StorageSystem storage,
            BlockStorageDevice device, List<Initiator> initiators) {
        // This will cause the VPlexBackendManager to generate an ExportMask
        // for the first volume and then reuse it by finding it from the
        // database for subsequent volumes.
        // Use this , if you really don't care about the # masks created on
        // theArray
        return new HashMap<URI, ExportMask>();
    }

    @Override
    public ExportMask refreshExportMask(StorageSystem storage, BlockStorageDevice device,
            ExportMask mask) {
        // Use this ,if you really don't care about the details of existing
        // masks on Array.
        return mask;
    }

    @Override
    public Set<Map<URI, List<StoragePort>>> getPortGroups(
            Map<URI, List<StoragePort>> allocatablePorts, Map<URI, NetworkLite> networkMap,
            URI varrayURI, int nInitiatorGroups) {
        Set<Map<URI, List<StoragePort>>> portGroups = new HashSet<Map<URI, List<StoragePort>>>();
        Map<URI, Integer> portsAllocatedPerNetwork = new HashMap<URI, Integer>();
        // Port Group is always 1 for XtremIO as of now.

        for (URI netURI : allocatablePorts.keySet()) {
            Integer nports = allocatablePorts.get(netURI).size() / XTREMIO_NUM_PORT_GROUP;
            portsAllocatedPerNetwork.put(netURI, nports);
        }

        StoragePortsAllocator allocator = new StoragePortsAllocator();

        for (int i = 0; i < XTREMIO_NUM_PORT_GROUP; i++) {
            Map<URI, List<StoragePort>> portGroup = new HashMap<URI, List<StoragePort>>();
            StringSet portNames = new StringSet();
            for (URI netURI : allocatablePorts.keySet()) {
                NetworkLite net = networkMap.get(netURI);
                List<StoragePort> allocatedPorts = allocatePorts(allocator,
                        allocatablePorts.get(netURI), portsAllocatedPerNetwork.get(netURI), net,
                        varrayURI);
                portGroup.put(netURI, allocatedPorts);
                allocatablePorts.get(netURI).removeAll(allocatedPorts);
                for (StoragePort port : allocatedPorts) {
                    portNames.add(port.getPortName());
                }
            }
            portGroups.add(portGroup);
            _log.info(String.format("Port Group %d: %s", i, portNames.toString()));

        }
        return portGroups;
    }

    private List<StoragePort> allocatePorts(StoragePortsAllocator allocator,
            List<StoragePort> candidatePorts, int portsRequested, NetworkLite net, URI varrayURI) {
        return VPlexBackEndOrchestratorUtil.allocatePorts(allocator, candidatePorts, portsRequested, net, varrayURI,
                simulation, _blockScheduler, _dbClient);
    }

    @Override
    public StringSetMap configureZoning(Map<URI, List<StoragePort>> portGroup,
            Map<String, Map<URI, Set<Initiator>>> initiatorGroup, Map<URI, NetworkLite> networkMap) {
        return VPlexBackEndOrchestratorUtil.configureZoning(portGroup, initiatorGroup, networkMap);
    }

    @Override
    public Method createOrAddVolumesToExportMaskMethod(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
            Map<URI, Integer> volumeMap, TaskCompleter completer) {
        return new Workflow.Method("createOrAddVolumesToExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumeMap, completer);
    }

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

            if (!exportMask.hasAnyVolumes()) {
                // We are creating this ExportMask on the hardware! (Maybe not
                // the first time though...)
                // Fetch the Initiators
                List<Initiator> initiators = new ArrayList<Initiator>();
                for (String initiatorId : exportMask.getInitiators()) {
                    Initiator initiator = _dbClient.queryObject(Initiator.class,
                            URI.create(initiatorId));
                    if (initiator != null) {
                        initiators.add(initiator);
                    }
                }
                // Fetch the targets
                List<URI> targets = new ArrayList<URI>();
                for (String targetId : exportMask.getStoragePorts()) {
                    targets.add(URI.create(targetId));
                }
                if (volumeMap != null) {
                    for (URI volume : volumeMap.keySet()) {
                        exportMask.addVolume(volume, volumeMap.get(volume));
                    }
                }
                _dbClient.persistObject(exportMask);
                device.doExportGroupCreate(array, exportMask, volumeMap, initiators, targets,
                        completer);
            } else {
                device.doExportAddVolumes(array, exportMask, volumeMap, completer);
            }

        } catch (Exception ex) {
            _log.error("Failed to create or add volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex
                    .addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }

    }

    @Override
    public Method deleteOrRemoveVolumesFromExportMaskMethod(URI arrayURI,
            URI exportGroupURI, URI exportMaskURI,
            List<URI> volumes, TaskCompleter completer) {
        return new Workflow.Method("deleteOrRemoveVolumesFromExportMask", arrayURI,
                exportGroupURI, exportMaskURI, volumes, completer);
    }

    @Override
    public void deleteOrRemoveVolumesFromExportMask(URI arrayURI, URI exportGroupURI, URI exportMaskURI,
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

            // Make sure the completer will complete the workflow. This happens
            // on rollback case.
            if (!completer.getOpId().equals(stepId))
                completer.setOpId(stepId);

            Set<String> remainingVolumes = new HashSet<String>();
            if (exportMask.getVolumes() != null)
                remainingVolumes.addAll(exportMask.getVolumes().keySet());
            for (URI volume : volumes) {
                remainingVolumes.remove(volume.toString());
            }
            // If so, delete the ExportMask.
            if (remainingVolumes.isEmpty()
                    && (exportMask.getExistingVolumes() == null || exportMask.getExistingVolumes()
                            .isEmpty())) {
                device.doExportGroupDelete(array, exportMask, completer);
            } else {
                device.doExportRemoveVolumes(array, exportMask, volumes, completer);
            }
        } catch (Exception ex) {
            _log.error("Failed to delete or remove volumes to export mask for vmax: ", ex);
            VPlexApiException vplexex = DeviceControllerExceptions.vplex
                    .addStepsForCreateVolumesFailed(ex);
            WorkflowStepCompleter.stepFailed(stepId, vplexex);
        }
    }

    public void setSimulation(boolean simulation) {
        this.simulation = simulation;
    }

    public WorkflowService getWorkflowService() {
        return _workflowService;
    }

    public void setWorkflowService(WorkflowService _workflowService) {
        this._workflowService = _workflowService;
    }
}
