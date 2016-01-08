/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.net.URI;
import java.util.List;
import java.util.Map;

import com.emc.storageos.plugins.common.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class ExportWorkflowEntryPoints implements Controller {
    private static final Logger _log = LoggerFactory.getLogger(ExportWorkflowEntryPoints.class);
    private static volatile String _beanName;
    private Map<String, MaskingOrchestrator> _orchestratorMap;
    private DbClient _dbClient;

    public ExportWorkflowEntryPoints() {

    }

    public void setName(String beanName) {
        _beanName = beanName;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setOrchestratorMap(Map<String, MaskingOrchestrator> orchestratorMap) {
        _orchestratorMap = orchestratorMap;
    }

    public MaskingOrchestrator getOrchestrator(String deviceType) {
        MaskingOrchestrator orchestrator = _orchestratorMap.get(deviceType);
        if (orchestrator == null) {
            // we will use orchestrator for external device
            orchestrator = _orchestratorMap.get(Constants.EXTERNALDEVICE);
            if (orchestrator == null) {
                throw DeviceControllerException.exceptions.invalidSystemType(deviceType);
            }
        }
        return orchestrator;
    }

    public static ExportWorkflowEntryPoints getInstance() {
        return (ExportWorkflowEntryPoints) ControllerServiceImpl.getBean(_beanName);
    }

    // ================== Static methods to create Workflow.Method
    // ======================

    public static Workflow.Method exportGroupCreateMethod(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap, List<URI> initiatorURIs) {
        return new Workflow.Method("exportGroupCreate", storageURI, exportGroupURI, volumeMap,
                initiatorURIs);
    }

    public static Workflow.Method exportGroupUpdateMethod(URI storageURI,
            URI exportGroupURI,
            Workflow storageWorkflow) {
        return new Workflow.Method("exportGroupUpdate", storageURI, exportGroupURI,
                storageWorkflow);
    }

    public static Workflow.Method exportGroupDeleteMethod(URI storageURI, URI exportGroupURI) {
        return new Workflow.Method("exportGroupDelete", storageURI, exportGroupURI);
    }

    public static Workflow.Method exportAddVolumesMethod(URI storageURI, URI exportGroupURI,
            Map<URI, Integer> volumeMap) {
        return new Workflow.Method("exportAddVolumes", storageURI, exportGroupURI, volumeMap);
    }

    public static Workflow.Method exportRemoveVolumesMethod(URI storageURI, URI exportGroupURI,
            List<URI> volumes) {
        return new Workflow.Method("exportRemoveVolumes", storageURI, exportGroupURI, volumes);
    }

    public static Workflow.Method exportAddInitiatorsMethod(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs) {
        return new Workflow.Method("exportAddInitiators", storageURI, exportGroupURI, initiatorURIs);
    }

    public static Workflow.Method exportRemoveInitiatorsMethod(URI storageURI, URI exportGroupURI,
            List<URI> initiatorURIs) {
        return new Workflow.Method("exportRemoveInitiators", storageURI, exportGroupURI,
                initiatorURIs);
    }

    public static Workflow.Method exportGroupChangePathParamsMethod(URI storageURI,
            URI exportGroupURI, URI volumeURI) {
        return new Workflow.Method("exportGroupChangePathParams",
                storageURI, exportGroupURI, volumeURI);
    }

    public static Workflow.Method exportChangePolicyAndLimitsMethod(URI storageURI,
            URI exportMaskURI, URI exportGroupURI, List<URI> volumeURIs, URI newVpoolURI, boolean rollback) {
        return new Workflow.Method("exportChangePolicyAndLimits",
                storageURI, exportMaskURI, exportGroupURI, volumeURIs, newVpoolURI, rollback);
    }

    public static Workflow.Method changeAutoTieringPolicyMethod(URI storageURI,
            List<URI> volumeURIs, URI newVpoolURI, boolean rollback) {
        return new Workflow.Method("changeAutoTieringPolicy",
                storageURI, volumeURIs, newVpoolURI, rollback);
    }

    // ====================== Methods to call Masking Orchestrator
    // ======================

    public void exportGroupCreate(URI storageURI, URI exportGroupURI, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs, String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupCreate(storageURI, exportGroupURI, initiatorURIs, volumeMap,
                    token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions.exportGroupCreateFailed(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    /**
     * This function is called from a main workflow that performs updated to an export
     * group on may storage arrays. This step performs the updates for one array. It
     * simply invokes the workflow that was pre-created that will do the needed adds/removes,
     * 
     * @param storageURI the storage array of the masks to be updated
     * @param exportGroupURI the export group URI
     * @param storageWorkflow the pre-created workflow for this storage array.
     * @param token the task Id
     * @throws ControllerException when an exception is encountered in the workflow execution.
     */
    public void exportGroupUpdate(URI storageURI, URI exportGroupURI,
            Workflow storageWorkflow,
            String token)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupUpdate(storageURI, exportGroupURI, storageWorkflow, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportGroupUpdateFailed(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void exportGroupDelete(URI storageURI, URI exportGroupURI, String token)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupDelete(storageURI, exportGroupURI, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportGroupDeleteFailed(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void exportAddVolumes(URI storageURI, URI exportGroupURI, Map<URI, Integer> volumeMap,
            String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupAddVolumes(storageURI, exportGroupURI, volumeMap, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportAddVolumes(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void exportRemoveVolumes(URI storageURI, URI exportGroupURI, List<URI> volumes,
            String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupRemoveVolumes(storageURI, exportGroupURI, volumes, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportRemoveVolumes(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void exportAddInitiators(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs,
            String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupAddInitiators(storageURI, exportGroupURI, initiatorURIs, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportAddInitiators(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void exportRemoveInitiators(URI storageURI, URI exportGroupURI, List<URI> initiatorURIs,
            String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupRemoveInitiators(storageURI, exportGroupURI, initiatorURIs,
                    token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportRemoveInitiators(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    /**
     * Changes the PathParams (e.g. maxPaths, pathsPerInitiator) for a volume in all the
     * ExportMasks containing that volume in an Export Group.
     * 
     * @param storageURI -- URI of storage system containing the volume.
     * @param exportGroupURI -- URI of Export Group to be processed.
     * @param volumeURI -- URI of volume that is chaning VPool parameters for PathParams
     * @param token -- String for completers.
     * @throws ControllerException
     */
    public void exportGroupChangePathParams(URI storageURI, URI exportGroupURI,
            URI volumeURI, String token)
            throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            orchestrator.exportGroupChangePathParams(storageURI, exportGroupURI, volumeURI, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportGroupChangePathParams(e);
            WorkflowStepCompleter.stepFailed(token, exception);
        }
    }

    public void exportChangePolicyAndLimits(URI storageURI, URI exportMaskURI,
            URI exportGroupURI, List<URI> volumeURIs, URI newVpoolURI,
            boolean rollback, String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            ((AbstractBasicMaskingOrchestrator) orchestrator)
                    .exportGroupChangePolicyAndLimits(storageURI, exportMaskURI,
                            exportGroupURI, volumeURIs, newVpoolURI, rollback, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .exportChangePolicyAndLimits(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }

    public void changeAutoTieringPolicy(URI storageURI, List<URI> volumeURIs, URI newVpoolURI,
            boolean rollback, String token) throws ControllerException {
        try {
            WorkflowStepCompleter.stepExecuting(token);
            DiscoveredSystemObject storage =
                    ExportWorkflowUtils.getStorageSystem(_dbClient, storageURI);
            MaskingOrchestrator orchestrator = getOrchestrator(storage.getSystemType());
            ((AbstractBasicMaskingOrchestrator) orchestrator)
                    .changeAutoTieringPolicy(storageURI, volumeURIs, newVpoolURI, rollback, token);
        } catch (Exception e) {
            DeviceControllerException exception = DeviceControllerException.exceptions
                    .changeAutoTieringPolicy(e);
            WorkflowStepCompleter.stepFailed(token, exception);
            throw exception;
        }
    }
}
