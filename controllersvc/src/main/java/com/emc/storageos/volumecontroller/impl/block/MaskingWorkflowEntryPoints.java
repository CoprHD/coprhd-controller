/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DataObject.Flag;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.InvokeTestFailure;
import com.emc.storageos.volumecontroller.BlockStorageDevice;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveInitiatorCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskRemoveVolumeCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportOrchestrationTask;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.RollbackExportGroupCreateCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.utils.ExportOperationContext;
import com.emc.storageos.volumecontroller.placement.BlockStorageScheduler;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.base.Joiner;

/**
 * Consider this class to hold the Workflow.Method implementations that will be called.
 */
public class MaskingWorkflowEntryPoints implements Controller {
    private static final Logger _log = LoggerFactory.getLogger(MaskingWorkflowEntryPoints.class);
    private static volatile String _beanName;
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
        String deviceType = storage.getSystemType();
        BlockStorageDevice storageDevice = _devices.get(deviceType);
        if (storageDevice == null) {
            // we will use external device
            storageDevice = _devices.get(Constants.EXTERNALDEVICE);
            if (storageDevice == null) {
                throw DeviceControllerException.exceptions.invalidSystemType(deviceType);
            }
        }
        return storageDevice;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
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
     * @param storageURI
     *            - URI of the Storage array.
     * @param exportGroupURI
     *            - URI of the ExportGroup
     * @param exportMaskURI
     *            - The URI of the Export Mask.
     * @param volumeMap
     *            - A map of Volume URI to HUL Integer value.
     * @param initiatorURIs
     *            - A list of Initiator URIs.
     * @param targets
     *            - A list of URIs representing Targets.
     * @param taskCompleter
     *            - The ExportCreateCompleter.
     * @param token
     *            - A String token that can be used to update the
     *            WorkflowTaskCompleter.
     * @throws com.emc.storageos.volumecontroller.ControllerException
     *
     */
    public void doExportGroupCreate(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs, List<URI> targets,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupCreate(%s, %s, %s, [%s], [%s], [%s], %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumeMap != null ? Joiner.on(',').join(volumeMap.entrySet()) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                targets != null ? Joiner.on(',').join(targets) : "No Target Ports",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            List<Initiator> initiators = _dbClient.queryObject(Initiator.class,
                    initiatorURIs);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            getDevice(storage).doExportCreate(storage, exportMask, volumeMap,
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
     * @param storageURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param contextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     *
     * @throws ControllerException
     */
    public void rollbackExportGroupCreate(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            String contextKey,
            String token) throws ControllerException {
        ExportTaskCompleter taskCompleter = new RollbackExportGroupCreateCompleter(exportGroupURI, exportMaskURI, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        ExportOperationContext context = null;
        try {
            context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
            WorkflowService.getInstance().storeStepData(token, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
        }
        _log.info("Rolling back operations: " + context);
        doExportGroupDelete(storageURI, exportGroupURI, exportMaskURI, null, null, taskCompleter, token);
    }

    /**
     * Zoning map update entry point
     */
    public void doExportMaskZoningMapUpdate(URI exportGroupURI, URI storageURI,
            String token) throws ControllerException {
        _log.info("START - doExportMaskZoningMapUpdate");
        WorkflowStepCompleter.stepExecuting(token);

        try {
            ExportGroup eg = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, eg);

            // There will be only export mask for the create export group use case,
            // so fetch the 0th URI
            if (!exportMasks.isEmpty()) {
	            ExportMask mask = exportMasks.get(0);	
	            _blockScheduler.updateZoningMap(mask, eg.getVirtualArray(), exportGroupURI);
            }

            WorkflowStepCompleter.stepSucceded(token);
        } catch (final InternalException e) {
            _log.error("Encountered an exception", e);
            WorkflowStepCompleter.stepFailed(token, e);
        } catch (final Exception e) {
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
            URI storageURI, String token) throws ControllerException {
        _log.info("START - rollbackExportMaskZoningMapUpdate");
        WorkflowStepCompleter.stepExecuting(token);
        // No-op
        WorkflowStepCompleter.stepSucceded(token);
        _log.info("END - rollbackExportMaskZoningMapUpdate");
    }

    public void doExportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs,
            TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupAddVolumes(%s, %s, %s, [%s], [%s], %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumeMap != null ? Joiner.on(',').join(volumeMap.entrySet()) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = new ArrayList<>();
            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
            }

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_001);

            getDevice(storage).doExportAddVolumes(storage, exportMask, initiators,
                    volumeMap, taskCompleter);

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
     * @param storageURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param volumeMap
     *            [in] - Map of Volume URI to HLU Integer value
     * @param initiatorURIs
     *            [in] - Impacted initiators
     * @param contextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
    public void rollbackExportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs,
            String contextKey, String token) throws ControllerException {
        List<URI> list = new ArrayList<URI>();
        list.addAll(volumeMap.keySet());
        ExportTaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                list, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
            WorkflowService.getInstance().storeStepData(token, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
        }

        doExportGroupRemoveVolumes(storageURI, exportGroupURI, exportMaskURI, list,
                initiatorURIs, taskCompleter, token);
    }
    
    /**
     * Rollback entry point. This is a wrapper around the exportGroupRemoveVolumes
     * operation, which requires that we create a specific completer using the token
     * that's passed in. This token is generated by the rollback processing.
     *
     * @param storageURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportGroupURIs
     *            [in] - All ExportGroup URIs that the export mask associates with
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param volumeMap
     *            [in] - Map of Volume URI to HLU Integer value
     * @param initiatorURIs
     *            [in] - Impacted initiators
     * @param contextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
    public void rollbackExportGroupAddVolumes(URI storageURI, URI exportGroupURI,
            List<URI> exportGroupURIs,
            URI exportMaskURI,
            Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs,
            String contextKey, String token) throws ControllerException {
        List<URI> list = new ArrayList<URI>();
        list.addAll(volumeMap.keySet());
        ExportTaskCompleter taskCompleter = new ExportMaskRemoveVolumeCompleter(exportGroupURI, exportMaskURI,
                list, token);
        taskCompleter.setExportGroups(exportGroupURIs);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.
        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
            WorkflowService.getInstance().storeStepData(token, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
        }

        doExportGroupRemoveVolumes(storageURI, exportGroupURI, exportMaskURI, list,
                initiatorURIs, taskCompleter, token);
    }

    public void doExportGroupDelete(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeURIs,
            List<URI> initiatorURIs, TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupDelete(%s, %s, %s, [%s], [%s], %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);

            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_018);
            getDevice(storage).doExportDelete(storage, exportMask, volumeURIs, initiatorURIs, taskCompleter);
            _log.info(String.format("%s end", call));
            // doExportDelete is responsible for calling the completer at this point. No code allowed after this point.
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
            List<URI> initiatorURIs,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveVolumes(%s, %s, %s, [%s], [%s], %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = new ArrayList<>();
            if (initiatorURIs != null && !initiatorURIs.isEmpty()) {
                initiators = _dbClient.queryObject(Initiator.class, initiatorURIs);
            }

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_017);

            getDevice(storage).doExportRemoveVolumes(storage, exportMask, volumeURIs,
                    initiators, taskCompleter);

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
            List<URI> initiatorURIs,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveVolumesCleanup(%s, %s, [%s], [%s], %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportGroup exportGroup = _dbClient
                    .queryObject(ExportGroup.class, exportGroupURI);

            exportGroup.removeVolumes(volumeURIs);

            // If there are no masks associated with this export group, and it's an internal (VPLEX/RP)
            // export group, delete the export group automatically.
            if ((exportGroup.checkInternalFlags(Flag.INTERNAL_OBJECT)) &&
                    (exportGroup == null || exportGroup.getExportMasks() == null || 
                     exportGroup.getExportMasks().isEmpty())) {
                _dbClient.markForDeletion(exportGroup);
            } else {
                _dbClient.updateObject(exportGroup);
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
            URI exportMaskURI,
            List<URI> volumeURIs,
            List<URI> initiatorURIs,
            List<URI> newSps,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupAddInitiators(%s, %s, %s, %s, %s, %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                newSps != null ? Joiner.on(',').join(newSps) : "No Target Ports",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = _dbClient
                    .queryObject(Initiator.class, initiatorURIs);

            getDevice(storage).doExportAddInitiators(storage, exportMask,
                    volumeURIs, initiators, newSps, taskCompleter);

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
     * @param storageURI
     *            [in] - StorageSystem URI
     * @param exportGroupURI
     *            [in] - ExportGroup URI
     * @param exportMaskURI
     *            [in] - ExportMask URI
     * @param volumeURIs
     *            [in] - Impacted volume URIs
     * @param initiatorURIs
     *            [in] - List of Initiator URIs
     * @param contextKey
     *            [in] - context token
     * @param token
     *            [in] - String token generated by the rollback processing
     * @throws ControllerException
     */
    public void rollbackExportGroupAddInitiators(URI storageURI, URI exportGroupURI,
            URI exportMaskURI, List<URI> volumeURIs,
            List<URI> initiatorURIs,
            String contextKey, String token) throws ControllerException {
        ExportTaskCompleter taskCompleter = new ExportMaskRemoveInitiatorCompleter(exportGroupURI, exportMaskURI,
                initiatorURIs, token);
        // Take the context of the step in flight and feed it into our current step
        // in order to only perform rollback of operations we successfully performed.

        try {
            ExportOperationContext context = (ExportOperationContext) WorkflowService.getInstance().loadStepData(contextKey);
            WorkflowService.getInstance().storeStepData(token, context);
        } catch (ClassCastException e) {
            _log.info("Step {} has stored step data other than ExportOperationContext. Exception: {}", token, e);
        }
        doExportGroupRemoveInitiators(storageURI, exportGroupURI, exportMaskURI,
                volumeURIs, initiatorURIs, true, taskCompleter, token);
    }

    public void doExportGroupRemoveInitiators(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            List<URI> volumeURIs,
            List<URI> initiatorURIs,
            boolean removeTargets,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExportGroupRemoveInitiators(%s, %s, %s, [%s], [%s], %s, %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumeURIs != null ? Joiner.on(',').join(volumeURIs) : "No Volumes",
                initiatorURIs != null ? Joiner.on(',').join(initiatorURIs) : "No Initiators",
                removeTargets,
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            ExportMask exportMask = _dbClient
                    .queryObject(ExportMask.class, exportMaskURI);
            StorageSystem storage = _dbClient
                    .queryObject(StorageSystem.class, storageURI);
            List<Initiator> initiators = _dbClient
                    .queryObject(Initiator.class, initiatorURIs);

            // Test mechanism to invoke a failure. No-op on production systems.
            InvokeTestFailure.internalOnlyInvokeTestFailure(InvokeTestFailure.ARTIFICIAL_FAILURE_016);

            List<URI> targetPorts = ExportUtils.getRemoveInitiatorStoragePorts(exportMask, initiators, _dbClient);
            getDevice(storage).doExportRemoveInitiators(storage, exportMask,
                    volumeURIs, initiators, removeTargets ? targetPorts : null, taskCompleter);
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
        String call = String.format("doExportGroupToCleanExportMask(%s, %s, %s)",
                exportGroupURI.toString(), exportMaskURI.toString(), taskCompleter.getOpId());
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
            URI exportMaskURI, List<URI> volumesToRemove, TaskCompleter taskCompleter,
            String token) throws ControllerException {
        String call = String.format("doExportGroupToCleanVolumesInExportMask(%s, %s, [%s], %s)",
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                volumesToRemove != null ? Joiner.on(',').join(volumesToRemove) : "No Volumes",
                taskCompleter.getOpId());
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
    
    public void doExportMaskAddPaths(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, List<URI>> newPaths,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        
        String call = String.format("doExporMaskAddPaths(%s, %s, %s, %s, %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                newPaths != null ? Joiner.on(',').withKeyValueSeparator("=").join(newPaths) : "No path",
                taskCompleter.getOpId());
        try {
            _log.info(call + " starts");
            WorkflowStepCompleter.stepExecuting(token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            
            getDevice(storage).doExportAddPaths(storage, exportMaskURI, newPaths, taskCompleter);

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
    
    public void doExportMaskRemovePaths(URI storageURI, URI exportGroupURI,
            URI exportMaskURI,
            Map<URI, List<URI>> adjustedPaths,
            Map<URI, List<URI>> removePaths,
            TaskCompleter taskCompleter, String token) throws ControllerException {
        String call = String.format("doExporMaskRemovePaths(%s, %s, %s, %s, %s)",
                storageURI.toString(),
                exportGroupURI.toString(),
                exportMaskURI.toString(),
                removePaths != null ? Joiner.on(',').withKeyValueSeparator("=").join(removePaths) : "No path",
                taskCompleter.getOpId());
        try {
            WorkflowStepCompleter.stepExecuting(token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            
            getDevice(storage).doExportRemovePaths(storage, exportMaskURI, adjustedPaths, removePaths, taskCompleter);

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
    
    public void doExportChangePortGroupAddPaths(URI storageURI, URI exportGroupURI, URI newMaskURI, URI oldMaskURI, 
            URI portGroupURI, TaskCompleter taskCompleter, String token) {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
            getDevice(storage).doExportChangePortGroupAddPaths(storage, newMaskURI, oldMaskURI, portGroupURI, taskCompleter);
        } catch (final InternalException e) {
            _log.info("doExportChangePortGroup Encountered an exception", e);
            taskCompleter.error(_dbClient, e);
        } catch (final Exception e) {
            _log.info("doExportChangePortGroup Encountered an exception", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            taskCompleter.error(_dbClient, serviceError);
        }
    }
    
    /**
     * No-op workflow step to allow rollback to continue.
     *
     * @param stepId    The workflow step ID.
     * @throws WorkflowException
     */
    public void rollbackMethodNull(String stepId) throws WorkflowException {
        WorkflowStepCompleter.stepSucceded(stepId);
    }
    
    /**
     * Roll back change port group delete mask
     * @param storageURI
     * @param exportGroupURI
     * @param exportMaskURI
     * @param contextKey
     * @param token
     * @throws ControllerException
     */
    public void rollbackChangePortGroupDeleteMask(URI storageURI, URI exportGroupURI, 
            URI exportMaskURI,
            String token) throws ControllerException {
        _log.info("Roll back change port group delete mask");
        ExportTaskCompleter taskCompleter = new ExportOrchestrationTask(exportGroupURI, token);

        StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);

        getDevice(storage).rollbackChangePortGroupRemovePaths(storage, exportGroupURI, exportMaskURI, taskCompleter);
    }
}
