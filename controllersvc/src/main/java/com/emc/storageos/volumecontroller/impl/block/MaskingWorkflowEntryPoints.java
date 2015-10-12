/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.networkcontroller.NetworkFCContext;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RollbackExportGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.smis.vmax.ExportOperationContext;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

/**
 * Consider this class to hold the Workflow.Method implementations that will be called.
 */
public class MaskingWorkflowEntryPoints implements Controller {
    private static final Logger _log =
            LoggerFactory.getLogger(MaskingWorkflowEntryPoints.class);
    private static volatile String _beanName;
    private NetworkDeviceController _networkDeviceController;
    private Map<String, BlockStorageDevice> _devices;
    private DbClient _dbClient;
    private BlockStorageScheduler _blockScheduler;

    public MaskingWorkflowEntryPoints() {

    }

    public void setName(String beanName) {
        _beanName = beanName;
    }

    public void setDevices(Map<String, BlockStorageDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private BlockStorageDevice getDevice(StorageSystem storage) {
        return _devices.get(storage.getSystemType());
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setNetworkDeviceController(NetworkDeviceController
            networkDeviceController) {
        _networkDeviceController = networkDeviceController;
    }

    public void setBlockScheduler(BlockStorageScheduler storageScheduler) {
        _blockScheduler = storageScheduler;
    }

    public static MaskingWorkflowEntryPoints getInstance() {
        return (MaskingWorkflowEntryPoints) ControllerServiceImpl.getBean(_beanName);
    }

    /**
     * Create the actual Export Mask on a Storage Array. This is called as a subtask
     * from exportGroupCreate().
     * 
     * @param storageURI - URI of the Storage array.
     * @param exportGroupURI - URI of the ExportGroup
     * @param volumeMap - A map of Volume URI to HUL Integer value.
     * @param initiatorURIs - A list of Initiator URIs.
     * @param exportMaskURI - The URI of the Export Mask.
     * @param targets - A list of URIs representing Targets.
     * @param taskCompleter - The ExportCreateCompleter.
     * @param token - A String token that can be used to update the
     *            WorkflowTaskCompleter.
     * @throws com.emc.storageos.volumecontroller.ControllerException
     * 
     */
    public void doExportGroupCreate(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs,
            URI exportMaskURI, List<URI> targets,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call =
                String.format("doExportGroupCreate(%s, %s, [%s], [%s], %s, [%s], %s)",
                        storageURI.toString(), exportGroupURI.toString(),
                        Joiner.on(',').join(volumeMap.entrySet()),
                        Joiner.on(',').join(initiatorURIs), exportMaskURI,
                        Joiner.on(',').join(targets), taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class,
                    initiatorURIs);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            getDevice(storage).doExportGroupCreate(storage, exportMask, volumeMap,
                    initiators, targets, taskCompleter);

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Rollback entry point. This is a wrapper around the exportGroupDelete operation,
     * which requires that we create a specific completer using the token that's passed
     * in. This token is generated by the rollback processing.
     * 
     * @param storageURI [in] - StorageSystem URI
     * @param exportGroupURI [in] - ExportGroup URI
     * @param exportMaskURI [in] - ExportMask URI
     * @param contextKey [in] - context token
     * @param token [in] - String token generated by the rollback processing
     * 
     * @throws ControllerException
     */
    public void rollbackExportGroupCreate(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            String contextKey,
            String token) throws ControllerException {
        ExportTaskCompleter taskCompleter =
                new RollbackExportGroupCreateCompleter(exportGroupURI, exportMaskURI, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
        WorkflowService.getInstance().storeStepData(token, context);
        _log.info("Rolling back operations: " + context);
        doExportGroupDelete(storageURI, exportGroupURI, exportMaskURI, taskCompleter, token);
    }

    /**
     * Zoning map update entry point
     */
    public void doExportMaskZoningMapUpdate(URI exportGroupURI, URI storageURI,
            String token) throws ControllerException
    {
        _log.info("START - doExportMaskZoningMapUpdate");
        WorkflowStepCompleter.stepExecuting(token);

        try
        {
            ExportGroup eg = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            List<URI> exportMaskURIs = StringSetUtil.stringSetToUriList(eg.getExportMasks());

            // There will be only export mask for the create export group use case,
            // so fetch the 0th URI
            ExportMask mask = _dbClient.queryObject(ExportMask.class, exportMaskURIs.get(0));

            _blockScheduler.updateZoningMap(mask, eg.getVirtualArray(), exportGroupURI);

            WorkflowStepCompleter.stepSucceded(token);

        } catch (final InternalException e)
        {
            _log.error("Encountered an exception", e);
            WorkflowStepCompleter.stepFailed(token, e);
        } catch (final Exception e)
        {
            _log.error("Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            WorkflowStepCompleter.stepFailed(token, serviceError);
        }

        _log.info("END - doExportMaskZoningMapUpdate");
    }

    /**
     * Zoning map update roll back entry point
     */
    public void rollbackExportMaskZoningMapUpdate(URI exportGroupURI,
            URI storageURI, String token) throws ControllerException
    {
        _log.info("START - rollbackExportMaskZoningMapUpdate");
        WorkflowStepCompleter.stepExecuting(token);
        // No-op
        WorkflowStepCompleter.stepSucceded(token);
        _log.info("END - rollbackExportMaskZoningMapUpdate");
    }

    public void doExportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, Map<URI, Integer> volumeMap,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupAddVolumes(%s, %s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(),
                Joiner.on(',').join(volumeMap.entrySet()), taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            getDevice(storage).doExportAddVolumes(storage, exportMask, volumeMap,
                    taskCompleter);

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Rollback entry point. This is a wrapper around the exportGroupRemoveVolumes
     * operation, which requires that we create a specific completer using the token
     * that's passed in. This token is generated by the rollback processing.
     * 
     * @param storageURI [in] - StorageSystem URI
     * @param exportGroupURI [in] - ExportGroup URI
     * @param exportMaskURI [in] - ExportMask URI
     * @param volumeMap [in] - Map of Volume URI to HLU Integer value
     * @param contextKey [in] - context token
     * @param token [in] - String token generated by the rollback processing
     * 
     * @throws ControllerException
     */
    public void rollbackExportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, Integer> volumeMap,
            String contextKey,
            String token) throws ControllerException {
        List<URI> list = new ArrayList<URI>();
        list.addAll(volumeMap.keySet());
        ExportTaskCompleter taskCompleter =
                new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                        list, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
        WorkflowService.getInstance().storeStepData(token, context);
        doExportGroupRemoveVolumes(storageURI, exportGroupURI, exportMaskURI, list,
                taskCompleter, token);
    }

    public void exportGroupDelete(URI storageURI, URI exportGroupURI,
            String token) throws ControllerException {
        String call = String.format("doExportGroupDelete(%s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(), token);
        NetworkFCContext networkContext = new NetworkFCContext();
        TaskCompleter taskCompleter = new ExportDeleteCompleter(exportGroupURI, false, token);
        try {
            _log.info(String.format("%s start", call));

            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class,
                    storageURI);

            if (exportGroup == null || exportGroup.getInactive()) {
                taskCompleter.ready(_dbClient);
                _log.info("Export Group {} lready deleted", exportGroupURI);
                return;
            }

            /**
             * If no export mask is found, nothing to be done. Task will be marked
             * complete by the last real export mask delete completion.
             */
            ExportMask exportMask = ExportMaskUtils.getExportMask(_dbClient,
                    exportGroup, storageURI);
            if (exportMask != null) {
                _log.info("export_delete: export mask exists");
                List<URI> exportMaskURIs = new ArrayList<URI>();
                List<URI> volumeURIs = ExportMaskUtils.getVolumeURIs(exportMask);
                exportMaskURIs.add(exportMask.getId());
                boolean zoningSuccess = _networkDeviceController
                        .zoneExportMasksDelete(exportGroupURI, exportMaskURIs, volumeURIs, UUID.randomUUID().toString());
                getDevice(storage).doExportGroupDelete(storage, exportMask,
                        taskCompleter);
            } else {
                _log.info("export_delete: no export mask, task completed");
                taskCompleter.ready(_dbClient);
            }

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupDelete(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call =
                String.format("doExportGroupDelete(%s, %s, %s, %s)",
                        storageURI.toString(), exportGroupURI.toString(),
                        exportMaskURI, taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            getDevice(storage).doExportGroupDelete(storage, exportMask, taskCompleter);

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupRemoveVolumes(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeURIs,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveVolumes(%s, %s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(),
                Joiner.on(',').join(volumeURIs), taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            getDevice(storage).doExportRemoveVolumes(storage, exportMask, volumeURIs,
                    taskCompleter);

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupRemoveVolumesCleanup(URI storageURI, URI exportGroupURI,
            List<URI> volumeURIs,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveVolumesCleanup(%s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(),
                Joiner.on(',').join(volumeURIs));
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportGroup exportGroup = _dbClient
                    .queryObject(ExportGroup.class, exportGroupURI);

            exportGroup.removeVolumes(volumeURIs);

            // If there are no masks associated with this export group, and it's an internal (VPLEX/RP)
            // export group, delete the export group automatically.
            if ((exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT)) &&
                    (exportGroup.getExportMasks() == null || exportGroup.getExportMasks().isEmpty())) {
                _dbClient.markForDeletion(exportGroup);
            } else {
                _dbClient.updateAndReindexObject(exportGroup);
            }

            taskCompleter.ready(_dbClient);
            _log.info(String.format("%s end", call));
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs,
            List<URI> newSps,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupAddInitiators(%s, %s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(),
                Joiner.on(',').join(initiatorURIs), taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class,
                    exportGroupURI);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = _dbClient
                    .queryObject(Initiator.class, initiatorURIs);

            getDevice(storage).doExportAddInitiators(storage, exportMask,
                    initiators, newSps, taskCompleter);

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Rollback entry point. This is a wrapper around the exportRemoveInitiators
     * operation, which requires that we create a specific completer using the token
     * that's passed in. This token is generated by the rollback processing.
     * 
     * @param storageURI [in] - StorageSystem URI
     * @param exportGroupURI [in] - ExportGroup URI
     * @param exportMaskURI [in] - ExportMask URI
     * @param initiatorURIs [in] - List of Initiator URIs
     * @param contextKey [in] - context token
     * @param token [in] - String token generated by the rollback processing
     * 
     * @throws ControllerException
     */
    public void rollbackExportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs,
            String contextKey,
            String token) throws ControllerException {
        ExportTaskCompleter taskCompleter =
                new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                        initiatorURIs, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
        WorkflowService.getInstance().storeStepData(token, context);
        doExportGroupRemoveInitiators(storageURI, exportGroupURI, exportMaskURI,
                initiatorURIs, true, taskCompleter, token);
    }

    public void doExportGroupRemoveInitiators(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> initiatorURIs,
            boolean removeTargets,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveInitiators(%s, %s, %s, %s, %s)",
                storageURI.toString(), exportGroupURI.toString(),
                Joiner.on(',').join(initiatorURIs), removeTargets, taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = _dbClient
                    .queryObject(Initiator.class, initiatorURIs);

            List<URI> targetPorts = _blockScheduler.getRemoveInitiatorStoragePorts(exportMask,
                    initiators);
            getDevice(storage).doExportRemoveInitiators(storage, exportMask,
                    initiators, removeTargets ? targetPorts : null, taskCompleter);

            // TODO - move this to the completer
            if (targetPorts != null && !targetPorts.isEmpty()) {
                for (URI targetPort : targetPorts) {
                    exportMask.removeTarget(targetPort);
                }
                _dbClient.updateAndReindexObject(exportMask);
            }

            _log.info(String.format("%s end", call));
        } catch (final InternalException e) {
            _log.info(call + " Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupToCleanExportMask(URI exportGroupURI, URI exportMaskURI,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupToCleanExportMask(%s, %s)",
                exportGroupURI.toString(), exportMaskURI);
        try {
            WorkflowStepCompleter.stepExecuting(token);
            taskCompleter.ready(_dbClient);
            _log.info(String.format("%s end", call));
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    public void doExportGroupToCleanVolumesInExportMask(URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeToRemove, TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupToCleanVolumesInExportMask(%s, %s)",
                exportGroupURI.toString(), exportMaskURI);
        try {
            WorkflowStepCompleter.stepExecuting(token);
            taskCompleter.ready(_dbClient);
            _log.info(String.format("%s end", call));
        } catch (final Exception e) {
            _log.info(call + " Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

}
