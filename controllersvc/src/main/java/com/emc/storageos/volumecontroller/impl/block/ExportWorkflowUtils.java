/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.networkcontroller.impl.NetworkDeviceController;
import com.emc.storageos.networkcontroller.impl.NetworkZoningParam;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportMaskAddPathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ZoningAddPathsCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ZoningRemovePathsCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowException;
import com.emc.storageos.workflow.WorkflowRestartedException;
import com.emc.storageos.workflow.WorkflowService;
import com.google.common.base.Joiner;

public class ExportWorkflowUtils {

    private static final Logger _log =
            LoggerFactory.getLogger(ExportWorkflowUtils.class);
    private DbClient _dbClient;
    private ExportWorkflowEntryPoints _exportWfEntryPoints;
    private WorkflowService _workflowSvc;
    private NetworkDeviceController networkDeviceController;


    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public WorkflowService getWorkflowService() {
        return _workflowSvc;
    }

    public void setWorkflowService(WorkflowService workflowService) {
        _workflowSvc = workflowService;
    }

    public void setExportWorkflowEntryPoints(ExportWorkflowEntryPoints entryPoints) {
        _exportWfEntryPoints = entryPoints;
    }
    
    public void setNetworkDeviceController(NetworkDeviceController networkDeviceController) {
        this.networkDeviceController = networkDeviceController;
    }

    public String generateExportGroupCreateWorkflow(Workflow workflow, String wfGroupId,
            String waitFor, URI storage,
            URI export,
            Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        // Filter the addedInitiators for non VPLEX system by the Export Group varray.
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, export);
        initiatorURIs = ExportUtils.filterNonVplexInitiatorsByExportGroupVarray(
                exportGroup, initiatorURIs, storage, _dbClient);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportGroupCreateMethod(storage, export,
                        volumeMap, initiatorURIs);

        Workflow.Method rollback =
                ExportWorkflowEntryPoints.exportGroupDeleteMethod(storage, export);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Creating export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, rollback, waitFor);
    }

    /**
     * Creates the workflow for one export mask (storage system) for an update export
     * group call. It creates a single step in the main workflow that wraps a workflow
     * with necessary steps to:
     * <ol>
     * <li>add block objects (volumes/snapshots)</li>
     * <li>remove volumes</li>
     * <li>add initiators</li>
     * <li>remove initiators</li>
     * </ol>
     * The steps are created based on the diff between the current and the requested for
     * the storage system export mask
     * 
     * @param workflow the main workflow
     * @param wfGroupId the workflow group Id, if any
     * @param waitFor the id of a step on which this workflow has to wait, if any
     * @param storageUri the storage controller used to perform the export update.
     *            This can be either a block storage controller or protection
     *            controller.
     * @param exportGroupUri the export group being updated
     * @param exportMask the export mask for the storage system
     * @param addedBlockObjects the map of block objects to be added
     * @param removedBlockObjects the map of block objects to be removed
     * @param addedInitiators the new list of initiators to be added
     * @param removedInitiators the new list of initiators to be removed
     * @param blockStorageControllerUri the block storage controller. This will always
     *            be used for adding/removing initiators as we
     *            do not want a protection controller doing this.
     * @return the id of the wrapper step that was added to main workflow
     * @throws IOException
     * @throws WorkflowException
     * @throws WorkflowRestartedException
     */
    public String generateExportGroupUpdateWorkflow(Workflow workflow, String wfGroupId,
            String waitFor, URI storageUri,
            URI exportGroupUri,
            ExportMask exportMask,
            Map<URI, Integer> addedBlockObjects,
            Map<URI, Integer> removedBlockObjects,
            List<URI> addedInitiators, List<URI> removedInitiators, URI blockStorageControllerUri)
            throws IOException, WorkflowException, WorkflowRestartedException {

        // Filter the addedInitiators for non VPLEX system by the Export Group varray.
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupUri);
        addedInitiators = ExportUtils.filterNonVplexInitiatorsByExportGroupVarray(
                exportGroup, addedInitiators, storageUri, _dbClient);

        if (allCollectionsAreEmpty(addedBlockObjects, removedBlockObjects, addedInitiators, removedInitiators)) {
            _log.info(String.format("There is no export updated required for %s", storageUri.toString()));
            return null;
        }

        Workflow storageWorkflow = newWorkflow("storageSystemExportGroupUpdate", false, workflow.getOrchTaskId());
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageUri);
        String stepId = null;

        // We willLock the host/storage system duples necessary for the workflows.
        // There are two possibilities about locking. Here we just generate the lockKeys.
        List<URI> lockedInitiatorURIs = new ArrayList<URI>();
        lockedInitiatorURIs.addAll(addedInitiators);
        lockedInitiatorURIs.addAll(StringSetUtil.stringSetToUriList(exportGroup.getInitiators()));
        List<String> lockKeys = ControllerLockingUtil
                .getHostStorageLockKeys(_dbClient,
                        ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                        lockedInitiatorURIs, blockStorageControllerUri);

        // for initiators do add first and then remove to avoid having
        // the export mask getting deleted when all existing initiators
        // are getting replaced.
        if (addedInitiators != null && !addedInitiators.isEmpty()) {
            stepId = generateExportGroupAddInitiators(storageWorkflow, null,
                    stepId, exportGroupUri, blockStorageControllerUri, addedInitiators);
        }
        if (removedInitiators != null && !removedInitiators.isEmpty()) {
            stepId = generateExportGroupRemoveInitiators(storageWorkflow, null,
                    stepId, exportGroupUri, blockStorageControllerUri, removedInitiators);
        }
        // for volumes we must do remove first in case the same lun id
        // will be used by one of the added volumes. This may result in
        // the export mask getting deleted and then created. If this
        // ends being a problem, we would need to tackle this issue
        if (removedBlockObjects != null && !removedBlockObjects.isEmpty()) {
            stepId = generateExportGroupRemoveVolumes(storageWorkflow,
                    null, stepId, storageUri, exportGroupUri,
                    new ArrayList<URI>(removedBlockObjects.keySet()));
        }
        if (addedBlockObjects != null && !addedBlockObjects.isEmpty()) {
            stepId = generateExportGroupAddVolumes(storageWorkflow, null,
                    stepId, storageUri, exportGroupUri, addedBlockObjects);
        }

        if (exportMask == null) {
            if (addedInitiators == null) {
                addedInitiators = new ArrayList<URI>();
            }
            if (addedInitiators.isEmpty()) {
                addedInitiators.addAll(getInitiators(exportGroup));
            }

            // Add block volumes already in the export group
            if (exportGroup.getVolumes() != null) {
                for (String key : exportGroup.getVolumes().keySet()) {
                    BlockObject bobject = BlockObject.fetch(_dbClient, URI.create(key));
                    // TODO: CTRL-4638: The storage controller of an RP bookmark won't match this.
                    // Good idea to do a conversion similar to the ones done in CTRL-4571
                    // so RP device controller can remove the image access properly.
                    if (bobject.getStorageController().equals(storageUri)) {
                        addedBlockObjects.put(URI.create(key), Integer.valueOf(exportGroup.getVolumes().get(key)));
                    }
                }
            }

            // Acquire locks for the parent workflow.
            boolean acquiredLocks = getWorkflowService().acquireWorkflowLocks(
                    workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
            if (!acquiredLocks) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        "ExportMaskUpgrade: " + exportGroup.getLabel());
            }

            // Add the new block objects to the existing ones and send all down
            return generateExportGroupCreateWorkflow(workflow, wfGroupId, waitFor,
                    storageUri, exportGroupUri, addedBlockObjects, addedInitiators);
        }

        try {
            // Acquire locks for the storageWorkflow which is started just below.
            boolean acquiredLocks = getWorkflowService().acquireWorkflowLocks(
                    storageWorkflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
            if (!acquiredLocks) {
                throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                        "ExportMaskUpgrade: " + exportMask.getMaskName());
            }

            // There will not be a rollback step for the overall update instead
            // the code allows the user to retry update as needed.
            Workflow.Method method =
                    ExportWorkflowEntryPoints.exportGroupUpdateMethod(storageUri, exportGroupUri, storageWorkflow);
            return newWorkflowStep(workflow, wfGroupId,
                    String.format("Updating export on storage array %s (%s)",
                            storageSystem.getNativeGuid(), storageUri.toString()),
                    storageSystem, method, null, waitFor);
        } catch (Exception ex) {
            getWorkflowService().releaseAllWorkflowLocks(storageWorkflow);
            throw ex;
        }
    }

    public String generateExportGroupDeleteWorkflow(Workflow workflow, String wfGroupId,
            String waitFor, URI storage,
            URI export)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportGroupDeleteMethod(storage, export);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Deleting export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, null, waitFor);
    }

    public String generateExportGroupAddVolumes(Workflow workflow, String wfGroupId,
            String waitFor, URI storage,
            URI export, Map<URI, Integer> volumeMap)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportAddVolumesMethod(storage, export,
                        volumeMap);

        List<URI> volumeList = new ArrayList<URI>();
        volumeList.addAll(volumeMap.keySet());

        Workflow.Method rollback =
                ExportWorkflowEntryPoints.exportRemoveVolumesMethod(storage, export,
                        volumeList);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Adding volumes to export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, rollback, waitFor);
    }

    public String generateExportGroupRemoveVolumes(Workflow workflow, String wfGroupId,
            String waitFor, URI storage,
            URI export, List<URI> volumes)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportRemoveVolumesMethod(storage, export,
                        volumes);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Removing volumes from export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, null, waitFor);
    }

    public String generateExportGroupRemoveInitiators(Workflow workflow,
            String wfGroupId, String waitFor,
            URI export, URI storage,
            List<URI> initiatorURIs)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportRemoveInitiatorsMethod(storage, export,
                        initiatorURIs);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Removing initiators from export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, null, waitFor);
    }

    public String generateExportGroupAddInitiators(Workflow workflow, String wfGroupId,
            String waitFor, URI export, URI storage,
            List<URI> initiatorURIs)
            throws WorkflowException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storage);

        Workflow.Method method =
                ExportWorkflowEntryPoints.exportAddInitiatorsMethod(storage, export,
                        initiatorURIs);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Adding initiators from export on storage array %s (%s)",
                        storageSystem.getNativeGuid(), storage.toString()),
                storageSystem, method, rollbackMethodNullMethod(), waitFor);
    }

    /**
     * Generates a Workflow Step to change the path parameters of a Volume in a specific Export Group.
     * 
     * @param workflow
     * @param wfGroupId - Workflow group for the Step
     * @param waitFor - Wait on this step/group to complete in the workflow before execution
     * @param storageURI - Storage system containing the volume
     * @param exportGroupURI - Export group that has exported the volume
     * @param volumeURI - Volume we are changing the path parameters for
     * @return stepId for the generated workflow Step
     * @throws ControllerException
     */
    public String generateExportChangePathParams(Workflow workflow, String wfGroupId, String waitFor,
            URI storageURI, URI exportGroupURI, URI volumeURI)
            throws ControllerException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageURI);
        BlockObject volume = BlockObject.fetch(_dbClient, volumeURI);

        Workflow.Method method = ExportWorkflowEntryPoints.exportGroupChangePathParamsMethod(
                storageURI, exportGroupURI, volumeURI);
        return newWorkflowStep(workflow, wfGroupId,
                String.format("Updated Export PathParams on storage array %s (%s, args) for volume %s (%s)",
                        storageSystem.getNativeGuid(), storageURI, volume.getLabel(), volumeURI),
                storageSystem, method, null, waitFor);
    }

    /**
     * Generate a Workflow Step to change the Auto-tiering policy for the volumes
     * in a specific Export Mask.
     * 
     * @param workflow the workflow
     * @param wfGroupId - Workflow group for the Step
     * @param waitFor - Wait on this step/group to complete in the workflow before execution
     * @param storageURI the storage system uri
     * @param exportMaskURI the export mask uri
     * @param volumeURIs the volume uris
     * @param exportGroupURI
     * @param newVpoolURI the new vpool uri
     * @return stepId for the generated workflow Step
     * @throws ControllerException the controller exception
     */
    public String generateExportChangePolicyAndLimits(Workflow workflow,
            String wfGroupId, String waitFor, URI storageURI,
            URI exportMaskURI, URI exportGroupURI, List<URI> volumeURIs,
            URI newVpoolURI, URI oldVpoolURI) throws ControllerException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageURI);

        Workflow.Method method = ExportWorkflowEntryPoints.exportChangePolicyAndLimitsMethod(
                storageURI, exportMaskURI, exportGroupURI, volumeURIs, newVpoolURI, false);

        // same method is called as a roll back step by passing old vPool.
        // boolean is passed to differentiate it.
        Workflow.Method rollback = ExportWorkflowEntryPoints.exportChangePolicyAndLimitsMethod(
                storageURI, exportMaskURI, exportGroupURI, volumeURIs, oldVpoolURI, true);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Updating Auto-tiering Policy on storage array %s (%s, args) for volumes %s",
                        storageSystem.getNativeGuid(), storageURI, Joiner.on("\t").join(volumeURIs)),
                storageSystem, method, rollback, waitFor);
    }

    /**
     * Generate a Workflow Step to change the Auto-tiering policy for the volumes
     * in a specific Export Mask.
     * 
     * @param workflow the workflow
     * @param wfGroupId - Workflow group for the Step
     * @param waitFor - Wait on this step/group to complete in the workflow before execution
     * @param storageURI the storage system uri
     * @param volumeURIs the volume uris
     * @param newVpoolURI the new vpool uri
     * @param oldVpoolURI the old vpool uri *
     * @return stepId for the generated workflow Step
     * @throws ControllerException the controller exception
     */
    public String generateChangeAutoTieringPolicy(Workflow workflow,
            String wfGroupId, String waitFor, URI storageURI,
            List<URI> volumeURIs, URI newVpoolURI, URI oldVpoolURI) throws ControllerException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageURI);

        Workflow.Method method = ExportWorkflowEntryPoints.changeAutoTieringPolicyMethod(
                storageURI, volumeURIs, newVpoolURI, false);

        // same method is called as a roll back step by passing old vPool.
        // boolean is passed to differentiate it.
        Workflow.Method rollback = ExportWorkflowEntryPoints.changeAutoTieringPolicyMethod(
                storageURI, volumeURIs, oldVpoolURI, true);

        return newWorkflowStep(workflow, wfGroupId,
                String.format("Updating Auto-tiering Policy on storage array %s (%s, args) for volumes %s",
                        storageSystem.getNativeGuid(), storageURI, Joiner.on("\t").join(volumeURIs)),
                storageSystem, method, rollback, waitFor);
    }

    /**
     * Wrapper for WorkflowService.getNewWorkflow
     * 
     * @param name - Name of workflow
     * @param rollback - Boolean indicating whether rollback should be invoked or not.
     * @param opId - Operation id
     * @return Workflow object
     * @throws com.emc.storageos.workflow.WorkflowRestartedException
     */
    public Workflow newWorkflow(String name, boolean rollback, String opId)
            throws WorkflowRestartedException {
        return _workflowSvc.getNewWorkflow(_exportWfEntryPoints, name, rollback, opId);
    }
    
    /**
     * Wrapper for WorkflowService.createStep. The method expects that the method and
     * rollback (if specified) are in the ExportWorkflowEntryPoints class.
     * 
     * @param workflow - Workflow in which to add the step
     * @param groupId - String pointing to the group id of the step
     * @param description - String description of the step
     * @param storageSystem - StorageSystem object to which operation applies
     * @param method - Step method to be called
     * @param rollback - Step rollback method to be call (if any)
     * @param waitFor - String of groupId of step to wait for
     * 
     * @return String the stepId generated for the step.
     * @throws WorkflowException
     */
    public String newWorkflowStep(Workflow workflow,
            String groupId, String description,
            DiscoveredSystemObject storageSystem,
            Workflow.Method method, Workflow.Method rollback,
            String waitFor)
            throws WorkflowException {
        if (groupId == null) {
            groupId = method.getClass().getSimpleName();
        }
        return workflow.createStep(groupId, description,
                waitFor, storageSystem.getId(),
                storageSystem.getSystemType(), ExportWorkflowEntryPoints.class, method,
                rollback, null);
    }

    /**
     * Wrapper for WorkflowService.createStep. The method expects that the method and
     * rollback (if specified) are in the ExportWorkflowEntryPoints class.
     * 
     * @param workflow - Workflow in which to add the step
     * @param groupId - String pointing to the group id of the step
     * @param description - String description of the step
     * @param storageSystem - StorageSystem object to which operation applies
     * @param method - Step method to be called
     * @param rollback - Step rollback method to be call (if any)
     * @param waitFor - String of groupId of step to wait for
     * @param setSuspend - Sets the suspend flag on the step
     * @param suspendMessage -- String message that will be displayed on suspend
     * 
     * @return String the stepId generated for the step.
     * @throws WorkflowException
     */
    public String newWorkflowStep(Workflow workflow,
            String groupId, String description,
            DiscoveredSystemObject storageSystem,
            Workflow.Method method, Workflow.Method rollback,
            String waitFor, boolean setSuspend, String suspendMessage)
            throws WorkflowException {
        if (groupId == null) {
            groupId = method.getClass().getSimpleName();
        }
        String stepId =  workflow.createStep(groupId, description,
                waitFor, storageSystem.getId(),
                storageSystem.getSystemType(), ExportWorkflowEntryPoints.class, method,
                rollback, setSuspend, null);
        workflow.setSuspendedStepMessage(stepId, suspendMessage);
        return stepId;
    }

    /**
     * Returns a DiscoveredDataObject which can either point to a StorageSystem or
     * ProtectionSystem object.
     * 
     * @param dbClient - DbClient object
     * @param storageURI - URI pointing to storage array object
     * @return DiscoveredDataObject
     */
    public static DiscoveredSystemObject getStorageSystem(DbClient dbClient,
            URI storageURI) {
        DiscoveredSystemObject ddo;
        if (URIUtil.isType(storageURI, ProtectionSystem.class)) {
            ddo = dbClient.queryObject(ProtectionSystem.class, storageURI);
        } else {
            ddo = dbClient.queryObject(StorageSystem.class, storageURI);
        }
        return ddo;
    }

    /**
     * Returns a list of Initiator URIs in the ExportGroup
     * 
     * @param exportGroup
     * @return
     */
    private List<URI> getInitiators(ExportGroup exportGroup) {
        List<URI> initiatorURIs = new ArrayList<URI>();
        if (exportGroup.getInitiators() != null) {
            for (String initiator : exportGroup.getInitiators()) {
                try {
                    URI initiatorURI = new URI(initiator);
                    initiatorURIs.add(initiatorURI);
                } catch (URISyntaxException ex) {
                    _log.error("Bad URI syntax: " + initiator);
                }
            }
        }
        return initiatorURIs;
    }

    private boolean allCollectionsAreEmpty(Map<URI, Integer> addedBlockObjects, Map<URI, Integer> removedBlockObjects,
            List<URI> addedInitiators, List<URI> removedInitiators) {
        return (addedBlockObjects != null && addedBlockObjects.isEmpty() &&
                removedBlockObjects != null && removedBlockObjects.isEmpty() &&
                addedInitiators != null && addedInitiators.isEmpty() &&
                removedInitiators != null && removedInitiators.isEmpty());
    }

    /**
     * Creates a rollback workflow method that does nothing, but allows rollback
     * to continue to prior steps back up the workflow chain.
     *
     * @return A workflow method
     */
    private Workflow.Method rollbackMethodNullMethod() {
        return new Workflow.Method("rollbackMethodNull");
    }
    
    /**
     * Generates a Workflow Step to add paths in a storage system for a specific Export Mask.
     * 
     * @param workflow - Workflow in which to add the step
     * @param wfGroupId - String pointing to the group id of the step
     * @param waitFor - Wait on this step/group to complete in the workflow before execution
     * @param storageURI - Storage system URI
     * @param exportGroupURI - Export group URI
     * @param exportMask --  the Export Mask
     * @param addedPaths - Paths going to be added or retained
     * @param removedPath - Paths going to be removed
     * @return - Step id
     * @throws ControllerException
     */
    public String generateExportAddPathsWorkflow(Workflow workflow, String wfGroupId, String waitFor,
            URI storageURI, URI exportGroupURI, ExportMask exportMask, Map<URI, List<URI>> adjustedPaths, Map<URI, List<URI>> removedPaths) 
                    throws ControllerException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageURI);

        Workflow.Method method = ExportWorkflowEntryPoints.exportAddPathsMethod(
                storageURI, exportGroupURI, exportMask.getId(), adjustedPaths, removedPaths);
        String stepDescription = String.format("Export add paths mask %s hosts %s", exportMask.getMaskName(), 
                ExportMaskUtils.getHostNamesInMask(exportMask, _dbClient));
        return newWorkflowStep(workflow, wfGroupId, stepDescription,
                storageSystem, method, rollbackMethodNullMethod(), waitFor);
    }
    
    /**
     * Generate workflow step for remove path masking in a storage system for a specific export mask.
     * 
     * @param workflow
     * @param wfGroupId
     * @param waitFor
     * @param storageURI
     * @param exportGroupURI
     * @param exportMask
     * @param adjustedpaths
     * @param removePaths
     * @param isPending
     * @param suspendMessage
     * @return
     * @throws ControllerException
     */
    public String generateExportRemovePathsWorkflow(Workflow workflow, String wfGroupId, String waitFor,
            URI storageURI, URI exportGroupURI, ExportMask exportMask, Map<URI, List<URI>> adjustedPaths, 
            Map<URI, List<URI>> removePaths, boolean isPending, String suspendMessage) 
                    throws ControllerException {
        DiscoveredSystemObject storageSystem = getStorageSystem(_dbClient, storageURI);

        Workflow.Method method = ExportWorkflowEntryPoints.exportRemovePathsMethod(
                storageURI, exportGroupURI, exportMask.getId(), adjustedPaths, removePaths);
        String stepDescription = String.format("Export remove paths mask %s hosts %s", exportMask.getMaskName(), 
                ExportMaskUtils.getHostNamesInMask(exportMask, _dbClient));
        return newWorkflowStep(workflow, wfGroupId, stepDescription,
                storageSystem, method, rollbackMethodNullMethod(), waitFor, isPending, suspendMessage);
    }
    
    /**
     * Generate workflow step for add path zoning
     * 
     * @param workflow
     * @param wfGroupId
     * @param storageURI
     * @param exportGroupURI
     * @param adjustedPaths - The paths going to be added and/or retained
     * @param waitFor
     * @return
     * @throws ControllerException
     */
    public String generateZoningAddPathsWorkflow(Workflow workflow, String wfGroupId, URI systemURI, URI exportGroupURI, 
            Map<URI, Map<URI, List<URI>>> exportMaskNewPathsMap, Map<URI, List<URI>>newPaths, String waitFor) throws ControllerException {
        String zoningStep = workflow.createStepId();

        ExportTaskCompleter taskCompleter = new ZoningAddPathsCompleter(exportGroupURI, zoningStep, exportMaskNewPathsMap);
        List<URI> maskURIs = new ArrayList<URI> (exportMaskNewPathsMap.keySet());
        Workflow.Method zoningExecuteMethod = networkDeviceController.zoneExportAddPathsMethod(systemURI, exportGroupURI, 
                maskURIs, newPaths, taskCompleter);

        zoningStep = workflow.createStep(
                wfGroupId,
                "Zoning add paths subtask: " + exportGroupURI,
                waitFor, NullColumnValueGetter.getNullURI(),
                "network-system", networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);
        
        return zoningStep;
    }
    
    /**
     * Generate workflow step for remove paths zoning
     * 
     * @param workflow - workflow
     * @param wfGroupId - workflow group id
     * @param storageURI - system URI
     * @param exportGroupURI - export group URI
     * @param maskAjustedPathMap - adjusted paths per mask
     * @param maskRemovePaths - remove paths per mask
     * @param waitFor - wait for step
     * @return - generated step id
     * @throws ControllerException
     */
    public String generateZoningRemovePathsWorkflow(Workflow workflow, String wfGroupId, URI storageURI, URI exportGroupURI, 
            Map<URI, Map<URI, List<URI>>> maskAdjustedPathMap, Map<URI, Map<URI, List<URI>>> maskRemovePaths, String waitFor) 
                    throws ControllerException {

        String zoningStep = workflow.createStepId();
        ZoningRemovePathsCompleter taskCompleter = new ZoningRemovePathsCompleter(exportGroupURI, zoningStep, maskAdjustedPathMap);
        List<NetworkZoningParam> zoningParams = NetworkZoningParam.
                convertPathsToNetworkZoningParam(exportGroupURI, maskRemovePaths, _dbClient);
        Workflow.Method zoningExecuteMethod = networkDeviceController
                .zoneExportRemovePathsMethod(zoningParams, taskCompleter);
        zoningStep = workflow.createStep(
                wfGroupId,
                "Zoning subtask for remvoe paths: " + exportGroupURI,
                waitFor, NullColumnValueGetter.getNullURI(),
                "network-system", networkDeviceController.getClass(),
                zoningExecuteMethod, null, zoningStep);

        return zoningStep;
        
    }
}
