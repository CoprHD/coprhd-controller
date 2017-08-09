/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.block;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.computesystemcontroller.impl.ComputeSystemHelper;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.ExportPathParams;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.PerformancePolicy;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockRetryException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.protectioncontroller.ProtectionExportController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPDeviceExportController;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockPerformancePolicyChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportPortRebalanceCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolAutoTieringPolicyChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowState;
import com.google.common.base.Joiner;

/**
 * Controller level implementation of Export controller implementation. This will be
 * the entry point for export operations. This layer is pretty much a wrapper Workflow
 * enabled wrapper around the ExportOrchestrator implementation. All operations will
 * inspect the parameters and should determine the workflow steps required and invoke
 * the WorkflowService#executePlan method. Usual scenario will be to generated a
 * separate workflow step for each storage system that the masking operations will be
 * done against.
 */
public class BlockDeviceExportController implements BlockExportController {

    private DbClient _dbClient;
    private static final Logger _log =
            LoggerFactory.getLogger(BlockDeviceExportController.class);
    private ExportWorkflowUtils _wfUtils;

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setExportWorkflowUtils(ExportWorkflowUtils exportWorkflowUtils) {
        _wfUtils = exportWorkflowUtils;
    }

    /**
     * Export one or more volumes. The volumeToExports parameter has
     * all the information required to do the add volumes operation.
     *
     * @param export URI of ExportMask
     * @param volumeMap Volume-lun map to be part of the export mask
     * @param initiatorURIs List of URIs for the initiators to be added to the export mask
     * @param opId Operation ID
     * @throws com.emc.storageos.volumecontroller.ControllerException
     *
     */
    @Override
    public void exportGroupCreate(URI export, Map<URI, Integer> volumeMap,
            List<URI> initiatorURIs, String opId)
            throws ControllerException {
        ExportTaskCompleter taskCompleter = new ExportCreateCompleter(export, volumeMap, opId);
        Workflow workflow = null;
        try {
            // Do some initial sanitizing of the export parameters
            StringSetUtil.removeDuplicates(initiatorURIs);
            workflow = _wfUtils.newWorkflow("exportGroupCreate", false, opId);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, export);

            Map<URI, Map<URI, Integer>> storageToVolumes =
                    getStorageToVolumeMap(volumeMap);
            for (Map.Entry<URI, Map<URI, Integer>> entry : storageToVolumes.entrySet()) {
                List<String> lockKeys = ControllerLockingUtil
                        .getHostStorageLockKeys(_dbClient,
                                ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                                initiatorURIs, entry.getKey());
                boolean acquiredLocks = _wfUtils.getWorkflowService().acquireWorkflowLocks(
                        workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                if (!acquiredLocks) {
                    throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                            "ExportGroupCreate: " + exportGroup.getLabel());
                }

                // Initialize the Map of objects to export with all objects.
                Map<URI, Integer> objectsToAdd = new HashMap<URI, Integer>(entry.getValue());

                String waitFor = null;

                ProtectionExportController protectionController = getProtectionExportController();
                waitFor = protectionController.addStepsForExportGroupCreate(workflow, null, waitFor, export, objectsToAdd, entry.getKey(),
                        initiatorURIs);

                if (!objectsToAdd.isEmpty()) {
                    // There are no export BlockObjects tied to the current storage system that have an associated protection
                    // system. We can just create a step to call the block controller directly for export group create.
                    _log.info(String.format(
                            "Generating exportGroupCreates steps for objects %s associated with storage system [%s]",
                            objectsToAdd, entry.getKey()));
                _wfUtils.
                            generateExportGroupCreateWorkflow(workflow, null, waitFor,
                                    entry.getKey(), export, objectsToAdd, initiatorURIs);
            }
            }

            workflow.executePlan(taskCompleter, "Exported to all devices successfully.");
        } catch (Exception ex) {
            String message = "exportGroupCreate caught an exception.";
            _log.error(message, ex);
            _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Delete the export.
     *
     * @param export URI of ExportGroup
     * @param opId Operation ID
     * @throws com.emc.storageos.volumecontroller.ControllerException
     *
     */
    @Override
    public void exportGroupDelete(URI export, String opId) throws ControllerException {
        ExportTaskCompleter taskCompleter = new ExportDeleteCompleter(export, false, opId);
        Workflow workflow = null;
        try {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, export);
            if (exportGroup != null && exportGroup.getExportMasks() != null) {
                workflow = _wfUtils.newWorkflow("exportGroupDelete", false, opId);

                if (NullColumnValueGetter.isNullValue(exportGroup.getType())) {
                    // if the group type is null, it cannot be deleted 
                    // (VPLEX backend groups have a null export group type, for instance)
                    throw DeviceControllerException.exceptions.exportGroupDeleteUnsupported(exportGroup.forDisplay());
                }

                Set<URI> storageSystemURIs = new HashSet<URI>();
                // Use temp set to prevent ConcurrentModificationException
                List<ExportMask> tempExportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup);
                for (ExportMask tempExportMask : tempExportMasks) {               
                    List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                            _dbClient, ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                            StringSetUtil.stringSetToUriList(exportGroup.getInitiators()), tempExportMask.getStorageDevice());
                    boolean acquiredLocks = _wfUtils.getWorkflowService().acquireWorkflowLocks(
                            workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                    if (!acquiredLocks) {
                        throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                                "ExportGroupDelete: " + exportGroup.getLabel());
                    }
                    if (tempExportMask != null && tempExportMask.getVolumes() != null) {
                        List<URI> uriList = getExportRemovableObjects(
                                exportGroup, tempExportMask);
                        Map<URI, List<URI>> storageToVolumes = getStorageToVolumes(uriList);
                        for (URI storageURI : storageToVolumes.keySet()) {

                            if (!storageSystemURIs.contains(storageURI)) {
                                storageSystemURIs.add(storageURI);
                                _wfUtils.
                                        generateExportGroupDeleteWorkflow(workflow, null,
                                                null, storageURI, export);
                            }
                        }
                    } else {
                        exportGroup.removeExportMask(tempExportMask.getId());
                        _dbClient.persistObject(exportGroup);
                    }
                }
                workflow.executePlan(taskCompleter, "Removed export from all devices.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            String message = "exportGroupDelete caught an exception.";
            _log.error(message, ex);
            if (workflow != null) {
                _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow);
            }
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }

    }

    /**
     * Not all block objects in the export group are what they seem.
     * Some block objects are volumes that are associated with RP snapshots
     * Some volumes in the export mask don't belong to the export group being removed.
     * This method sorts out of relevant from the irrelevant and creates a real list of URIs to remove.
     *
     * @param exportGroup export group
     * @param exportMask export mask
     * @return URIs of block objects to remove
     */
    private List<URI> getExportRemovableObjects(ExportGroup exportGroup,
            ExportMask exportMask) {
        List<URI> uriList = new ArrayList<URI>();
        // Check to make sure:
        // A. The volume in the mask is even associated with this export group
        // B. If the volume is associated with an RP snapshot in the export, best to use the snapshot object instead
        // TODO: Make provisions or don't allow both target volume export plus RP snapshot export together, as they
        // map to the same volume.
        if (exportMask.getVolumes() != null) {
            for (URI volumeID : URIUtil.toURIList(exportMask.getVolumes().keySet())) {
                if (!exportGroup.hasBlockObject(volumeID)) {
                    // It's either not part of this export group, or it's a block volume associated
                    // with a RP snapshot.
                    if (exportGroup.getSnapshots() != null) {
                        for (URI snapshotID : URIUtil.toURIList(exportGroup.getSnapshots())) {
                            BlockObject bo = Volume.fetchExportMaskBlockObject(_dbClient, snapshotID);
                            // If we were returned a block object that isn't the same as the snapshot we're iterating over,
                            // and it's not null, then that means it's an RP target volume by definition.
                            if (bo != null && !bo.getId().equals(snapshotID) && !uriList.contains(snapshotID)) {
                                // Add this snapshot to the list of "volumes" to be removed from this mask,
                                // so it gets routed through RP Controller.
                                uriList.add(snapshotID);
                            }
                        }
                    }
                } else {
                    uriList.add(volumeID);
                }

            }
        }
        _log.info(String.format("Export Group being removed contains block objects: { %s }",
                exportGroup.getVolumes() != null ? Joiner.on(',').join(exportGroup.getVolumes().keySet()) : "NONE"));
        _log.info(String.format("Export Mask being analyzed contains block objects: { %s }",
                exportMask.getVolumes() != null ? Joiner.on(',').join(exportMask.getVolumes().keySet()) : "NONE"));
        _log.info(String.format("Block Objects being sent in for removal: { %s }",
                Joiner.on(',').join(uriList)));
        return uriList;
    }

    // =========================== UTILITY METHODS BELOW ================================

    /**
     * Takes in a map of blockObject URIs to Integer and converts that to a mapping of
     * Storage array to map of blockObject URIs to Integer. Basically,
     * separating the volumes by arrays.
     *
     * @param uriToHLU [in] - BlockObjects URIs to HLU Integer value
     * @return StorageSystem URI to BlockObjects URIs to HLU Integer value.
     * @throws IOException
     */
    private Map<URI, Map<URI, Integer>> getStorageToVolumeMap(Map<URI, Integer> uriToHLU) throws IOException {
        Map<URI, Map<URI, Integer>> map = new HashMap<URI, Map<URI, Integer>>();
        for (Map.Entry<URI, Integer> entry : uriToHLU.entrySet()) {
            BlockObject blockObject = BlockObject.fetch(_dbClient, entry.getKey());
            URI storage = blockObject.getStorageController();
            Map<URI, Integer> volumesForStorage = map.get(storage);
            if (volumesForStorage == null) {
                volumesForStorage = new HashMap<URI, Integer>();
                map.put(storage, volumesForStorage);
            }
            volumesForStorage.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /**
     * Get mapping of the storage arrays to the volumes based on the volumes list.
     * If any of the volumes are protected RP snapshots, all of the volumes/snaps for
     * that storage system should go through the protection controller so we don't fire
     * off multiple export group operations on the same export group concurrently.
     *
     * @param volumes - BlockObject URI list
     * @return Mapping of storage arrays (StorageSystem URI) to volumes list
     * @throws IOException
     */
    private Map<URI, List<URI>> getStorageToVolumes(Collection<URI> volumes) throws IOException {
        Map<URI, List<URI>> storageToVolumesMap = new HashMap<URI, List<URI>>();
        Map<URI, URI> storageToProtectionMap = new HashMap<URI, URI>();

        // First pick up any protection system controllers in the volumes, because if there are
        // any, all volumes/snapshots associated with that volume's storage system need to go
        // down that go to the protection controller together.
        for (URI uri : volumes) {
            BlockObject blockObject = BlockObject.fetch(_dbClient, uri);
            URI storage = getExportStorageController(blockObject);
            if (URIUtil.isType(storage, ProtectionSystem.class)) {
                List<URI> storageVolumes = storageToVolumesMap.get(storage);
                if (storageVolumes == null) {
                    storageVolumes = new ArrayList<URI>();
                    storageToVolumesMap.put(storage, storageVolumes);
                }
                storageVolumes.add(uri);

                // Add this storage system to the map ("storage" is the protection ctlr)
                storageToProtectionMap.put(blockObject.getStorageController(), storage);
            }
        }

        // Assemble a map of controllers that need to get called, and their respective volumes.
        for (URI uri : volumes) {
            BlockObject blockObject = BlockObject.fetch(_dbClient, uri);
            URI storage = getExportStorageController(blockObject);
            if (URIUtil.isType(storage, StorageSystem.class) || storageToProtectionMap.isEmpty()) {
                if (storageToProtectionMap.get(storage) != null) {
                    // Add this to the existing protection controller's list
                    storage = storageToProtectionMap.get(storage);
                }

                List<URI> storageVolumes = storageToVolumesMap.get(storage);
                if (storageVolumes == null) {
                    storageVolumes = new ArrayList<URI>();
                    storageToVolumesMap.put(storage, storageVolumes);
                }
                storageVolumes.add(uri);
            }
        }

        return storageToVolumesMap;
    }

    /**
     * Get the correct storage controller for the blockObject so the export operation
     * is completed successfully.
     *
     * @param blockObject block object, volume/snapshot
     * @return URI of a block or protection controller
     */
    private URI getExportStorageController(BlockObject blockObject) {
        URI storage = blockObject.getStorageController();

        // If the volume is an RP snapshot, those objects need to be handled by the RP controller
        if ((blockObject.getProtectionController() != null) &&
                (blockObject.getId().toString().contains("BlockSnapshot"))) {
            ProtectionSystem system = _dbClient.queryObject(ProtectionSystem.class, blockObject.getProtectionController());
            String deviceType = system.getSystemType();

            if (DiscoveredDataObject.Type.rp.name().equals(deviceType)) {
                storage = blockObject.getProtectionController();
            }
        }
        return storage;
    }

    @Override
    public void exportGroupUpdate(URI export,
            Map<URI, Integer> addedBlockObjectMap,
            Map<URI, Integer> removedBlockObjectMap,
            Set<URI> addedClusters, Set<URI> removedClusters,
            Set<URI> addedHosts, Set<URI> removedHosts, Set<URI> addedInitiators, Set<URI> removedInitiators, String opId)
            throws ControllerException {
        Map<URI, Map<URI, Integer>> addedStorageToBlockObjects =
                new HashMap<URI, Map<URI, Integer>>();
        Map<URI, Map<URI, Integer>> removedStorageToBlockObjects =
                new HashMap<URI, Map<URI, Integer>>();

        Workflow workflow = null;
        List<Workflow> workflowList = new ArrayList<>();
        try {
            computeDiffs(export,
                    addedBlockObjectMap, removedBlockObjectMap, addedStorageToBlockObjects,
                    removedStorageToBlockObjects, addedInitiators,
                    removedInitiators, addedHosts, removedHosts, addedClusters,
                    removedClusters);

            // Generate a flat list of volume/snap objects that will be added
            // to the export update completer so the completer will know what
            // to add upon task completion. We need not carry the block controller
            // into the completer, so we strip that out of the map for the benefit of
            // keeping the completer simple.
            Map<URI, Integer> addedBlockObjects = new HashMap<>();
            for (URI storageUri : addedStorageToBlockObjects.keySet()) {
                addedBlockObjects.putAll(addedStorageToBlockObjects.get(storageUri));
            }

            // Generate a flat list of volume/snap objects that will be removed
            // to the export update completer so the completer will know what
            // to remove upon task completion.
            Map<URI, Integer> removedBlockObjects = new HashMap<>();
            for (URI storageUri : removedStorageToBlockObjects.keySet()) {
                removedBlockObjects.putAll(removedStorageToBlockObjects.get(storageUri));
            }

            // Construct the export update completer with exactly which objects will
            // be removed and added when it is complete.
            ExportTaskCompleter taskCompleter = new ExportUpdateCompleter(export,
                    addedBlockObjects, removedBlockObjects,
                    addedInitiators, removedInitiators,
                    addedHosts, removedHosts,
                    addedClusters, removedClusters, opId);

            _log.info("Received request to update export group. Creating master workflow.");
            workflow = _wfUtils.newWorkflow("exportGroupUpdate", false, opId);
            _log.info("Task id {} and workflow uri {}", opId, workflow.getWorkflowURI());
            workflowList.add(workflow);
            for (URI storageUri : addedStorageToBlockObjects.keySet()) {
                _log.info("Creating sub-workflow for storage system {}", String.valueOf(storageUri));
                // TODO: Need to fix, getExportMask() returns a single mask,
                // but there could be more than 1 for a array and ExportGroup
                _wfUtils.
                        generateExportGroupUpdateWorkflow(workflow, null, null,
                                export, getExportMask(export, storageUri),
                                addedStorageToBlockObjects.get(storageUri),
                                removedStorageToBlockObjects.get(storageUri),
                                new ArrayList(addedInitiators), new ArrayList(removedInitiators), storageUri, workflowList);
            }
            
            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The updateExportWorkflow has {} steps. Starting the workflow.", workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Update the export group on all storage systems successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (LockRetryException ex) {
            /**
             * Added this catch block to mark the current workflow as completed so that lock retry will not get exception while creating new
             * workflow using the same taskid.
             */
            _log.info(String.format("Lock retry exception key: %s remaining time %d", ex.getLockIdentifier(),
                    ex.getRemainingWaitTimeSeconds()));
            for (Workflow workflow2 : workflowList) {
                if (workflow2 != null) {
                    boolean status = _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow2);
                    _log.info("Release locks from workflow {} status {}", workflow2.getWorkflowURI(), status);
                }
            }

            if (workflow != null && !NullColumnValueGetter.isNullURI(workflow.getWorkflowURI())
                    && workflow.getWorkflowState() == WorkflowState.CREATED) {
                com.emc.storageos.db.client.model.Workflow wf = _dbClient.queryObject(com.emc.storageos.db.client.model.Workflow.class,
                        workflow.getWorkflowURI());
                if (!wf.getCompleted()) {
                    _log.error("Marking the status to completed for the newly created workflow {}", wf.getId());
                    wf.setCompleted(true);
                    _dbClient.updateObject(wf);
                }
            }
            throw ex;
        } catch (Exception ex) {
            ExportTaskCompleter taskCompleter = new ExportUpdateCompleter(export, opId);
            String message = "exportGroupUpdate caught an exception.";
            _log.error(message, ex);
            for (Workflow workflow2 : workflowList) {
                if (workflow2 != null) {
                    boolean status = _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow2);
                    _log.info("Release locks from workflow {} status {}", workflow2.getWorkflowURI(), status);
                }
            }

            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    /**
     * Compute the maps of volumes to be added and removed and the lists of initiators
     * to be added and removed.
     *
     * @param expoUri the export group URI
     * @param addedBlockObjectsFromRequest : the map of block objects that were requested to be
     *            added. (Passed separately to avoid concurrency problem).
     * @param removedBlockObjectsFromRequest : the map of block objects that wee reqested
     *            to be removed.
     * @param addedBlockObjects a map to be filled with storage-system-to-added-volumed
     * @param removedBlockObjects a map to be filled with storage-system-to-removed-volumed
     * @param addedInitiators a list to be filled with added initiators
     * @param removedInitiators a list to be filled with removed initiators
     * @param addedHosts list of hosts to add
     * @param removedHosts list of hosts to remove
     * @param addedClusters list of clusters to add
     * @param removedClusters list of cluster to remove
     */
    private void computeDiffs(URI expoUri,
            Map<URI, Integer> addedBlockObjectsFromRequest,
            Map<URI, Integer> removedBlockObjectsFromRequest,
            Map<URI, Map<URI, Integer>> addedBlockObjects,
            Map<URI, Map<URI, Integer>> removedBlockObjects,
            Set<URI> addedInitiators,
            Set<URI> removedInitiators, Set<URI> addedHosts,
            Set<URI> removedHosts, Set<URI> addedClusters,
            Set<URI> removedClusters) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, expoUri);
        Map<URI, Integer> existingMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());

        // If there are existing volumes, make sure their controller is represented in the
        // addedBlockObjects and removedBlockObjects maps, even if nothing was added / or removed.
        // This is necessary because the addBlockObjects.keyset() is used by the caller to iterate
        // over the controllers needed for the workflow.
        if (exportGroup.getVolumes() != null) {
            _log.info("Existing export group volumes: " + Joiner.on(',').join(exportGroup.getVolumes().keySet()));
            Map<URI, Integer> existingBlockObjectMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
            for (Map.Entry<URI, Integer> existingBlockObjectEntry : existingBlockObjectMap.entrySet()) {
                BlockObject bo = BlockObject.fetch(_dbClient, existingBlockObjectEntry.getKey());

                _log.info("Existing block object {} in storage {}", bo.getId(), bo.getStorageController());

                // add an entry in each map for the storage system if not already exists
                getOrAddStorageMap(bo.getStorageController(), addedBlockObjects);
                getOrAddStorageMap(bo.getStorageController(), removedBlockObjects);
            }
        }

        // compute a map of storage-system-to-volumes for volumes to be added
        for (URI uri : addedBlockObjectsFromRequest.keySet()) {
            BlockObject bo = BlockObject.fetch(_dbClient, uri);

            // add an entry in each map for the storage system if not already exists
            getOrAddStorageMap(bo.getStorageController(), addedBlockObjects).put(uri, addedBlockObjectsFromRequest.get(uri));
            getOrAddStorageMap(bo.getStorageController(), removedBlockObjects);

            _log.info("Block object {} to add to storage: {}", bo.getId(), bo.getStorageController());
        }

        for (URI uri : removedBlockObjectsFromRequest.keySet()) {
            if (existingMap.containsKey(uri)) {
                BlockObject bo = BlockObject.fetch(_dbClient, uri);

                // add an empty map for the added blocks so that the two maps have the same keyset
                getOrAddStorageMap(bo.getStorageController(), addedBlockObjects);
                getOrAddStorageMap(bo.getStorageController(), removedBlockObjects).put(uri, existingMap.get(uri));

                _log.info("Block object {} to remove from storage: {}", bo.getId(), bo.getStorageController());
            }
        }

        for (URI clusterURI : addedClusters) {
            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterURI, Host.class, "cluster");
            addedHosts.addAll(hostUris);
        }

        for (URI clusterURI : removedClusters) {
            List<URI> hostUris = ComputeSystemHelper.getChildrenUris(_dbClient, clusterURI, Host.class, "cluster");
            removedHosts.addAll(hostUris);
        }

        for (URI hostURI : addedHosts) {
            addedInitiators.addAll(ComputeSystemHelper.getChildrenUris(_dbClient, hostURI, Initiator.class, "host"));
        }

        for (URI hostURI : removedHosts) {
            removedInitiators.addAll(ComputeSystemHelper.getChildrenUris(_dbClient, hostURI, Initiator.class, "host"));
        }
        
        _log.info("Initiators to add: {}", addedInitiators);
        _log.info("Initiators to remove: {}", removedInitiators);
        _log.info("Hosts to add: {}", addedHosts);
        _log.info("Hosts to remove: {}", removedHosts);
        _log.info("Clusters to add: {}", addedClusters);
        _log.info("Clusters to remove: {}", removedClusters);

    }

    private Map<URI, Integer> getOrAddStorageMap(URI storageUri,
            Map<URI, Map<URI, Integer>> map) {
        Map<URI, Integer> volumesForStorage = map.get(storageUri);
        if (volumesForStorage == null) {
            volumesForStorage = new HashMap<URI, Integer>();
            map.put(storageUri, volumesForStorage);
        }
        return volumesForStorage;
    }

    private ExportMask getExportMask(URI exportGroupUri, URI storageUri) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupUri);
    
        for (ExportMask exportMask : ExportMaskUtils.getExportMasks(_dbClient, exportGroup)) {
            try {               
                if (exportMask.getStorageDevice().equals(storageUri)) {
                    return exportMask;
                }
            } catch (Exception ex) {
                // I need to log the fact that I got a bad export mask URI
                _log.warn("Cannot get export mask for storage " + storageUri + " and export group " + exportGroupUri, ex);
            }
        }
        
        return null;
    }

    @Override
    public void updateVolumePathParams(URI volumeURI, URI newVpoolURI, String opId) throws ControllerException {
        _log.info("Received request to update Volume path parameters. Creating master workflow.");
        VolumeVpoolChangeTaskCompleter taskCompleter = null;
        Volume volume = null;
        try {
            // Read volume from database, update the Vpool to the new completer, and create task completer.
            volume = _dbClient.queryObject(Volume.class, volumeURI);
            URI oldVpoolURI = volume.getVirtualPool();
            List<URI> rollbackList = new ArrayList<URI>();
            List<Volume> updatedVolumes = new ArrayList<Volume>();
            rollbackList.add(volumeURI);
            // Check if it is a VPlex volume, and get backend volumes
            Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient, false);
            if (backendSrc != null) {
                // Change the back end volume's vpool too
                backendSrc.setVirtualPool(newVpoolURI);
                rollbackList.add(backendSrc.getId());
                updatedVolumes.add(backendSrc);
                // VPlex volume, check if it is distributed
                Volume backendHa = VPlexUtil.getVPLEXBackendVolume(volume, false, _dbClient, false);
                if (backendHa != null && backendHa.getVirtualPool() != null &&
                        backendHa.getVirtualPool().toString().equals(oldVpoolURI.toString())) {
                    backendHa.setVirtualPool(newVpoolURI);
                    rollbackList.add(backendHa.getId());
                    updatedVolumes.add(backendHa);
                }

            }
            // The VolumeVpoolChangeTaskCompleter will restore the old Virtual Pool in event of error.
            taskCompleter = new VolumeVpoolChangeTaskCompleter(rollbackList, oldVpoolURI, opId);
            volume.setVirtualPool(newVpoolURI);
            updatedVolumes.add(volume);
            _log.info(String.format("Changing VirtualPool PathParams for volume %s (%s) from %s to %s",
                    volume.getLabel(), volume.getId(), oldVpoolURI, newVpoolURI));
            _dbClient.updateAndReindexObject(updatedVolumes);
        } catch (Exception ex) {
            _log.error("Unexpected exception reading volume or generating taskCompleter: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volumeURI, opId);
            completer.error(_dbClient, serviceError);
        }

        try {
            Workflow workflow = _wfUtils.newWorkflow("updateVolumePathParams", false, opId);

            // Locate all the ExportMasks containing the given volume, and their Export Group.
            Map<ExportMask, ExportGroup> maskToGroupMap =
                    ExportUtils.getExportMasks(volume, _dbClient);
            Map<URI, StringSetMap> maskToZoningMap = new HashMap<URI, StringSetMap>();
            // Store the original zoning maps of the export masks to be used to restore in case of a failure
            for (ExportMask mask : maskToGroupMap.keySet()) {
                maskToZoningMap.put(mask.getId(), mask.getZoningMap());
            }
            taskCompleter.setMaskToZoningMap(maskToZoningMap);
            // Acquire all necessary locks for the workflow:
            // For each export group lock initiator's hosts and storage array keys.
            List<URI> initiatorURIs = new ArrayList<URI>();
            for (ExportGroup exportGroup : maskToGroupMap.values()) {
                initiatorURIs.addAll(StringSetUtil.stringSetToUriList(exportGroup.getInitiators()));
                List<String> lockKeys = ControllerLockingUtil
                        .getHostStorageLockKeys(_dbClient,
                                ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                                initiatorURIs, volume.getStorageController());
                initiatorURIs.clear();
                boolean acquiredLocks = _wfUtils.getWorkflowService().acquireWorkflowLocks(
                        workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                if (!acquiredLocks) {
                    throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                            "UpdateVolumePathParams: " + volume.getLabel());
                }

            }

            // These steps are serialized, which is required in case an ExportMask appears
            // in multiple Export Groups.
            String stepId = null;
            for (ExportGroup exportGroup : maskToGroupMap.values()) {
                stepId = _wfUtils.generateExportChangePathParams(workflow, "changePathParams",
                        stepId, volume.getStorageController(), exportGroup.getId(), volumeURI);
            }

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The updateVolumePathParams workflow has {} steps. Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Update the export group on all storage systems successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("Unexpected exception: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    @Override
    public void updatePolicyAndLimits(List<URI> volumeURIs, URI newVpoolURI, String opId) throws ControllerException {
        _log.info("Received request to update Auto-tiering policy. Creating master workflow.");
        VolumeVpoolAutoTieringPolicyChangeTaskCompleter taskCompleter = null;
        URI oldVpoolURI = null;
        List<Volume> volumes = new ArrayList<Volume>();
        List<Volume> vplexBackendVolumes = new ArrayList<Volume>();

        try {
            // Read volume from database, update the vPool to the new vPool
            // and update new auto tiering policy uri, and create task completer.
            volumes = _dbClient.queryObject(Volume.class, volumeURIs);
            VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class,
                    newVpoolURI);
            Map<URI, URI> oldVolToPolicyMap = new HashMap<URI, URI>();
            for (Volume volume : volumes) {
                oldVpoolURI = volume.getVirtualPool();

                volume.setVirtualPool(newVpoolURI);
                _log.info(String.format("Changing VirtualPool Auto-tiering Policy for volume %s (%s) from %s to %s",
                        volume.getLabel(), volume.getId(), oldVpoolURI, newVpoolURI));
                oldVolToPolicyMap.put(volume.getId(), volume.getAutoTieringPolicyUri());
                updateAutoTieringPolicyUriInVolume(volume, newVpool);

                // Check if it is a VPlex volume, and get backend volumes
                Volume backendSrc = VPlexUtil.getVPLEXBackendVolume(volume, true, _dbClient, false);
                if (backendSrc != null) {
                    // Change the back end volume's vPool too
                    backendSrc.setVirtualPool(newVpoolURI);
                    vplexBackendVolumes.add(backendSrc);
                    _log.info(String.format(
                            "Changing VirtualPool Auto-tiering Policy for VPLEX backend source volume %s (%s) from %s to %s",
                            backendSrc.getLabel(), backendSrc.getId(), oldVpoolURI, newVpoolURI));
                    oldVolToPolicyMap.put(backendSrc.getId(), backendSrc.getAutoTieringPolicyUri());
                    updateAutoTieringPolicyUriInVolume(backendSrc, newVpool);

                    // VPlex volume, check if it is distributed
                    Volume backendHa = VPlexUtil.getVPLEXBackendVolume(volume, false, _dbClient, false);
                    if (backendHa != null) {
                        VirtualPool newHAVpool = VirtualPool.getHAVPool(newVpool, _dbClient);
                        if (newHAVpool == null) { // it may not be set
                            newHAVpool = newVpool;
                        }
                        backendHa.setVirtualPool(newHAVpool.getId());
                        vplexBackendVolumes.add(backendHa);
                        _log.info(String.format(
                                "Changing VirtualPool Auto-tiering Policy for VPLEX backend distributed volume %s (%s) from %s to %s",
                                backendHa.getLabel(), backendHa.getId(), oldVpoolURI, newHAVpool.getId()));
                        oldVolToPolicyMap.put(backendHa.getId(), backendHa.getAutoTieringPolicyUri());
                        updateAutoTieringPolicyUriInVolume(backendHa, newHAVpool);
                    }
                }
            }
            _dbClient.updateObject(volumes);
            _dbClient.updateObject(vplexBackendVolumes);

            // The VolumeVpoolChangeTaskCompleter will restore the old Virtual Pool
            // and old auto tiering policy in event of error.
            // Assume all volumes belong to the same vPool. This should be take care by BlockService API.
            taskCompleter = new VolumeVpoolAutoTieringPolicyChangeTaskCompleter(volumeURIs, oldVpoolURI, oldVolToPolicyMap, opId);
        } catch (Exception ex) {
            _log.error("Unexpected exception reading volume or generating taskCompleter: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volumeURIs, opId);
            completer.error(_dbClient, serviceError);
        }

        try {
            Workflow workflow = _wfUtils.newWorkflow("updateAutoTieringPolicy", false, opId);

            /**
             * For VMAX:
             * get corresponding export mask for each volume
             * group volumes by export mask
             * create workflow step for each export mask.
             *
             * For VNX Block:
             * Policy is set on volume during its creation.
             * Whether it is exported or not, send all volumes
             * to update StorageTierMethodology property on them.
             * Create workflow step for each storage system.
             */

            // Use backend volumes list if it is VPLEX volume
            List<Volume> volumesToUse = !vplexBackendVolumes.isEmpty() ? vplexBackendVolumes : volumes;

            // move applicable volumes from all volumes list to a separate list.
            Map<URI, List<URI>> systemToVolumeMap = getVolumesToModify(volumesToUse);

            String stepId = null;
            for (URI systemURI : systemToVolumeMap.keySet()) {
                stepId = _wfUtils.generateExportChangePolicyAndLimits(workflow,
                        "updateAutoTieringPolicy", stepId, systemURI, null, null,
                        systemToVolumeMap.get(systemURI), newVpoolURI,
                        oldVpoolURI);
            }

            Map<URI, List<URI>> storageToNotExportedVolumesMap = new HashMap<URI, List<URI>>();
            Map<URI, List<URI>> exportMaskToVolumeMap = new HashMap<URI, List<URI>>();
            Map<URI, URI> maskToGroupURIMap = new HashMap<URI, URI>();
            for (Volume volume : volumesToUse) {
                // Locate all the ExportMasks containing the given volume
                Map<ExportMask, ExportGroup> maskToGroupMap = ExportUtils
                        .getExportMasks(volume, _dbClient);

                if (maskToGroupMap.isEmpty()) {
                    URI storageURI = volume.getStorageController();
                    StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageURI);
                    if (storage.checkIfVmax3()) {
                        if (!storageToNotExportedVolumesMap.containsKey(storageURI)) {
                            storageToNotExportedVolumesMap.put(storageURI, new ArrayList<URI>());
                        }
                        storageToNotExportedVolumesMap.get(storageURI).add(volume.getId());
                    }
                }

                for (ExportMask mask : maskToGroupMap.keySet()) {
                    if (!exportMaskToVolumeMap.containsKey(mask.getId())) {
                        exportMaskToVolumeMap.put(mask.getId(), new ArrayList<URI>());
                    }
                    exportMaskToVolumeMap.get(mask.getId()).add(volume.getId());

                    maskToGroupURIMap.put(mask.getId(), maskToGroupMap.get(mask).getId());
                }
            }

            VirtualPool oldVpool = _dbClient.queryObject(VirtualPool.class, oldVpoolURI);
            for (URI exportMaskURI : exportMaskToVolumeMap.keySet()) {
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                List<URI> exportMaskVolumes = exportMaskToVolumeMap.get(exportMaskURI);
                URI exportMaskNewVpool = newVpoolURI;
                URI exportMaskOldVpool = oldVpoolURI;
                Volume vol = _dbClient.queryObject(Volume.class, exportMaskVolumes.get(0));
                // For VPLEX backend distributed volumes, send in HA vPool
                // all volumes are already updated with respective new vPool
                if (Volume.checkForVplexBackEndVolume(_dbClient, vol)
                        && !newVpoolURI.equals(vol.getVirtualPool())) {
                    // backend distributed volume; HA vPool set in Vplex vPool
                    exportMaskNewVpool = vol.getVirtualPool();
                    VirtualPool oldHAVpool = VirtualPool.getHAVPool(oldVpool, _dbClient);
                    if (oldHAVpool == null) { // it may not be set
                        oldHAVpool = oldVpool;
                    }
                    exportMaskOldVpool = oldHAVpool.getId();
                }
                stepId = _wfUtils.generateExportChangePolicyAndLimits(workflow,
                        "updateAutoTieringPolicy", stepId,
                        exportMask.getStorageDevice(), exportMaskURI,
                        maskToGroupURIMap.get(exportMaskURI),
                        exportMaskVolumes, exportMaskNewVpool,
                        exportMaskOldVpool);
            }

            for (URI storageURI : storageToNotExportedVolumesMap.keySet()) {
                stepId = _wfUtils.generateChangeAutoTieringPolicy(workflow,
                        "updateAutoTieringPolicyForNotExportedVMAX3Volumes", stepId,
                        storageURI, storageToNotExportedVolumesMap.get(storageURI),
                        newVpoolURI, oldVpoolURI);
            }

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info(
                        "The updateAutoTieringPolicy workflow has {} step(s). Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter,
                        "Updated the export group on all storage systems successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("Unexpected exception: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }

    private void updateAutoTieringPolicyUriInVolume(Volume volume,
            VirtualPool newVpool) {
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, volume.getStorageController());
        URI policyURI = ControllerUtils
                .getAutoTieringPolicyURIFromVirtualPool(newVpool,
                        storageSystem, _dbClient);
        if (policyURI == null) {
            policyURI = NullColumnValueGetter.getNullURI();
        }
        volume.setAutoTieringPolicyUri(policyURI);
        _log.info("Updating Auto Tiering Policy for Volume {} with {}",
                volume.getLabel(), policyURI);
    }

    /**
     * Separates the VNX/HDS volumes to another list which is grouped based on system URI.
     * It also removes those volumes from the original list.
     *
     * @param volumes all volumes list
     * @return systemToVolumeMap [[system_uri, list of volumes to modify its tieringpolicy].
     */
    private Map<URI, List<URI>> getVolumesToModify(List<Volume> volumes) {
        Map<URI, List<URI>> systemToVolumeMap = new HashMap<URI, List<URI>>();
        Iterator<Volume> itr = volumes.iterator();
        while (itr.hasNext()) {
            Volume volume = itr.next();
            URI systemURI = volume.getStorageController();
            StorageSystem system = _dbClient.queryObject(
                    StorageSystem.class, systemURI);
            if (DiscoveredDataObject.Type.vnxblock.name().equalsIgnoreCase(
                    system.getSystemType())
                    || DiscoveredDataObject.Type.hds.name().equalsIgnoreCase(
                            system.getSystemType())) {
                if (!systemToVolumeMap.containsKey(systemURI)) {
                    systemToVolumeMap.put(systemURI, new ArrayList<URI>());
                }
                systemToVolumeMap.get(systemURI).add(volume.getId());
                itr.remove();
            }
        }
        return systemToVolumeMap;
    }
    
    @Override
    public void exportGroupPortRebalance(URI systemURI, URI exportGroupURI, URI varray, Map<URI, List<URI>> adjustedPaths, 
            Map<URI, List<URI>> removedPaths, ExportPathParams exportPathParam, boolean waitBeforeRemovePaths, 
            String opId) throws ControllerException {
        _log.info("Received request for paths adjustment. Creating master workflow.");
        ExportPortRebalanceCompleter taskCompleter = new ExportPortRebalanceCompleter(systemURI, exportGroupURI, opId, 
                exportPathParam);
        Workflow workflow = null;
        try {
            workflow = _wfUtils.newWorkflow("paths adjustment", false, opId);
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            if (exportGroup == null || exportGroup.getExportMasks() == null) {
                _log.info("No export group or export mask");
                taskCompleter.ready(_dbClient);
                return;
            }
            List<ExportMask> exportMasks = ExportMaskUtils.getExportMasks(_dbClient, exportGroup, systemURI);
            if (exportMasks == null || exportMasks.isEmpty()) {
                _log.info(String.format("No export mask found for this system %s", systemURI));
                taskCompleter.ready(_dbClient);
                return;
            }
            // Acquire all necessary locks for the workflow:
            // For each export group lock initiator's hosts and storage array keys.
            List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                    _dbClient, ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                    StringSetUtil.stringSetToUriList(exportGroup.getInitiators()), systemURI);
            boolean acquiredLocks = _wfUtils.getWorkflowService().acquireWorkflowLocks(
                    workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
            if (!acquiredLocks) {
                _log.error("Paths adjustment could not require locks");
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOpMsg("paths adjustment", "Could not acquire workflow loc");
                taskCompleter.error(_dbClient, serviceError);
                return;

            }
            
            String stepId = null;
            Map<URI, Map<URI, List<URI>>> maskAdjustedPathMap = new HashMap<URI, Map<URI, List<URI>>>();
            Map<URI, Map<URI, List<URI>>> maskRemovePathMap = new HashMap<URI, Map<URI, List<URI>>>();
            List<ExportMask> affectedMasks = new ArrayList<ExportMask>();
            for (ExportMask mask : exportMasks) {
                if (!ExportMaskUtils.exportMaskInVarray(_dbClient, mask, varray)) {
                	_log.info(String.format("Export mask %s (%s, args) is not in the designated varray %s ... skipping", 
                			mask.getMaskName(), mask.getId(), varray));
                	continue;
                }
                affectedMasks.add(mask);
                Map<URI, List<URI>> adjustedPathForMask = ExportMaskUtils.getAdjustedPathsForExportMask(mask, adjustedPaths, _dbClient);
                Map<URI, List<URI>> removedPathForMask = ExportMaskUtils.getRemovePathsForExportMask(mask, removedPaths);
                maskAdjustedPathMap.put(mask.getId(), adjustedPathForMask);
                maskRemovePathMap.put(mask.getId(), removedPathForMask);
                
            }
            
            List<URI> maskURIs = new ArrayList<URI> ();
            Map<URI, List<URI>> newPaths = ExportMaskUtils.getNewPaths(_dbClient, exportMasks, maskURIs, adjustedPaths);
            if (!newPaths.isEmpty()) {
                for (ExportMask mask : affectedMasks) {                    
                    URI maskURI = mask.getId();
                    stepId = _wfUtils.generateExportAddPathsWorkflow(workflow, "Export add paths", stepId, systemURI, exportGroup.getId(),
                            varray, mask, maskAdjustedPathMap.get(maskURI), maskRemovePathMap.get(maskURI));
                }
    
                stepId = _wfUtils.generateZoningAddPathsWorkflow(workflow, "Zoning add paths", systemURI, exportGroupURI, maskAdjustedPathMap,
                        newPaths, stepId);
                
                stepId = _wfUtils.generateHostRescanWorkflowSteps(workflow, newPaths, stepId);
                
                }

            
            
            if (removedPaths != null && !removedPaths.isEmpty() ) {
                if (waitBeforeRemovePaths) {
                    // Insert a step that will be suspended. When it resumes, it will re-acquire the lock keys,
                    // which are released when the workflow suspends.
                    workflow.setTreatSuspendRollbackAsTerminate(true);
                    String suspendMessage = "Adjust/rescan host/cluster paths. Press \"Resume\" to start removal of unnecessary paths."
                            + "\"Rollback\" will terminate the order without removing paths, leaving the added paths in place.";
                    Workflow.Method method = WorkflowService.acquireWorkflowLocksMethod(lockKeys, 
                            LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                    Workflow.Method rollbackNull = Workflow.NULL_METHOD;
                    stepId = _wfUtils.newWorkflowStep(workflow, "AcquireLocks",
                            "Suspending for user verification of host/cluster connectivity.", systemURI,
                            WorkflowService.class, method, rollbackNull, stepId, waitBeforeRemovePaths, suspendMessage);
                }
                
                // Iterate through the ExportMasks, generating a step to remove unneeded paths.
                for (ExportMask mask : affectedMasks) {
                    URI maskURI = mask.getId();
                    Map<URI, List<URI>> removingPaths = maskRemovePathMap.get(maskURI);
                    if (!removingPaths.isEmpty()) {
                        stepId = _wfUtils.generateExportRemovePathsWorkflow(workflow, "Export remove paths", stepId, 
                                systemURI, exportGroupURI, varray, mask, maskAdjustedPathMap.get(maskURI), removingPaths);
                    }
                }
                stepId = _wfUtils.generateZoningRemovePathsWorkflow(workflow, "Zoning remove paths", systemURI, exportGroupURI, maskAdjustedPathMap,
                        maskRemovePathMap, stepId);
                
                stepId = _wfUtils.generateHostRescanWorkflowSteps(workflow, removedPaths, stepId);
            }
            if (!workflow.getAllStepStatus().isEmpty()) {
                // update ExportPortRebalanceCompleter with affected export groups
                Set<URI> affectedExportGroups = new HashSet<URI> ();
                for (ExportMask mask : affectedMasks) {
                    List<ExportGroup> assocExportGroups = ExportMaskUtils.getExportGroups(_dbClient, mask);
                    for (ExportGroup eg : assocExportGroups) {
                        affectedExportGroups.add(eg.getId());
                    }
                }
                taskCompleter.setAffectedExportGroups(affectedExportGroups);
                _log.info("The Export paths adjustment workflow has {} steps. Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Executing port rebalance workflow.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("Unexpected exception: ", ex);
            if (workflow != null) {
                _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow);
            }
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }
    

    /**
     * Gets an instance of ProtectionExportController.
     * <p>
     * NOTE: This method currently only returns an instance of RPDeviceExportController. In the future, this will need to return other
     * protection export controllers if support is added.
     * 
     * @return the ProtectionExportController
     */
    private ProtectionExportController getProtectionExportController() {
        return new RPDeviceExportController(_dbClient, _wfUtils);
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void updatePerformancePolicy(List<URI> volumeURIs, URI newPerfPolicyURI, String opId) throws ControllerException {
        _log.info("Received request to update performance policy for volumes {} with policy {}", volumeURIs, newPerfPolicyURI);
        BlockPerformancePolicyChangeTaskCompleter taskCompleter = null;
        List<Volume> volumes = new ArrayList<Volume>();
        Map<URI, URI> oldVolumeToPolicyMap = new HashMap<URI, URI>();
        try {
            PerformancePolicy newPerfPolicy = _dbClient.queryObject(PerformancePolicy.class, newPerfPolicyURI);
            volumes = _dbClient.queryObject(Volume.class, volumeURIs);
            for (Volume volume : volumes) {
                // Store the current performance policy in the map for rollback.
                oldVolumeToPolicyMap.put(volume.getId(), volume.getPerformancePolicy());
                
                // Update the performance policy for the volume.
                volume.setPerformancePolicy(newPerfPolicyURI);
                
                // Update the auto tiering policy for the volume to reflect the 
                // auto tiering policy specified in the new performance policy.
                URI atpURI = ControllerUtils.getAutoTieringPolicyURIFromPerfPolicy(newPerfPolicy, volume, _dbClient);
                if (atpURI == null) {
                    atpURI = NullColumnValueGetter.getNullURI();
                }
                volume.setAutoTieringPolicyUri(atpURI);
            }
            
            // Create the task completer which will restore the old performance policy and
            // auto tiering policy on the volume in the event of failure.
            taskCompleter = new BlockPerformancePolicyChangeTaskCompleter(volumeURIs, oldVolumeToPolicyMap, opId);
            
            // Lastly, update the volumes in the database.
            _dbClient.updateObject(volumes);

        } catch (Exception ex) {
            _log.error("Unexpected exception configuring tasks for performance policy change: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            VolumeWorkflowCompleter completer = new VolumeWorkflowCompleter(volumeURIs, opId);
            completer.error(_dbClient, serviceError);
        }

        try {
            Workflow workflow = _wfUtils.newWorkflow("updatePerformancePolicy", false, opId);

            /**
             * For VNX/HDS Block:
             * Policy is set on volume during its creation.
             * Whether it is exported or not, send all volumes
             * to update the auto tiering policy specified in
             * the new performance policy, which is the only
             * modifiable performance policy attribute that 
             * can be changed for these platforms. Create a 
             * workflow step for each storage system.
             *
             * For VMAX:
             * Get corresponding export mask for each volume
             * and group volumes by export mask. Create a 
             * workflow step for each export mask to modify
             * the auto tiering policy, compression, host
             * IO bandwidth limit, and/or host I/O IOPS
             * limit as specified by the new performance policy
             */

            // This function will map VNX and HDS by storage system and 
            // remove them from the volumes list.
            Map<URI, List<URI>> systemToVolumeMap = getVolumesToModify(volumes);

            // Create the steps to modify the policy for the volumes on
            // each system, if any.
            String stepId = null;
            for (URI systemURI : systemToVolumeMap.keySet()) {
                stepId = _wfUtils.generateExportChangePerformancePolicy(workflow,
                        "updatePerformancePolicy", stepId, systemURI, null, null,
                        systemToVolumeMap.get(systemURI), newPerfPolicyURI,
                        oldVolumeToPolicyMap);
            }

            // For the remaining VMAX volumes, for those that are exported, 
            // group them by export mask. For those that are not exported, map
            // them by storage system.
            Map<URI, List<URI>> storageToNotExportedVolumesMap = new HashMap<URI, List<URI>>();
            Map<URI, List<URI>> exportMaskToVolumeMap = new HashMap<URI, List<URI>>();
            Map<URI, URI> maskToGroupURIMap = new HashMap<URI, URI>();
            for (Volume volume : volumes) {
                // Locate all the export masks containing the volume.
                Map<ExportMask, ExportGroup> maskToGroupMap = ExportUtils.getExportMasks(volume, _dbClient);
                if (maskToGroupMap.isEmpty()) {
                    // The volume is not exported, so add to the system map.
                    URI systemURI = volume.getStorageController();
                    StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemURI);
                    // If not VMAX3, performance policy attributes cannot be updated.
                    if (storageSystem.checkIfVmax3()) {
                        if (!storageToNotExportedVolumesMap.containsKey(systemURI)) {
                            storageToNotExportedVolumesMap.put(systemURI, new ArrayList<URI>());
                        }
                        storageToNotExportedVolumesMap.get(systemURI).add(volume.getId());
                    }
                }

                // The volume is exported, so add it list of volumes for 
                // each of its export masks.
                for (ExportMask mask : maskToGroupMap.keySet()) {
                    if (!exportMaskToVolumeMap.containsKey(mask.getId())) {
                        exportMaskToVolumeMap.put(mask.getId(), new ArrayList<URI>());
                    }
                    exportMaskToVolumeMap.get(mask.getId()).add(volume.getId());
                    maskToGroupURIMap.put(mask.getId(), maskToGroupMap.get(mask).getId());
                }
            }

            // Create a step for each export mask to update the performance
            // policy attributes for the volumes in that mask.
            for (URI exportMaskURI : exportMaskToVolumeMap.keySet()) {
                ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
                List<URI> exportMaskVolumes = exportMaskToVolumeMap.get(exportMaskURI);
                stepId = _wfUtils.generateExportChangePerformancePolicy(workflow,
                        "updatePerformancePolicy", stepId, exportMask.getStorageDevice(),
                        exportMaskURI, maskToGroupURIMap.get(exportMaskURI), exportMaskVolumes,
                        newPerfPolicyURI, oldVolumeToPolicyMap);
            }

            // Create a step for each system. If not exported, the only change will
            // be to change the auto tiering policy for the volumes to that specified
            // in the new performance policy. Later if the volume is exported, the
            // values for compression, host I/O bandwidth limit, and host I/O IOPS
            // limit from the new policy will be applied.
            for (URI storageURI : storageToNotExportedVolumesMap.keySet()) {
                stepId = _wfUtils.generateExportChangePerformancePolicy(workflow,
                        "updatePerformancePolicy", stepId, storageURI, null, null,
                        storageToNotExportedVolumesMap.get(storageURI),
                        newPerfPolicyURI, oldVolumeToPolicyMap);
            }

            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The update performance policy workflow has {} step(s). Starting the workflow.",
                        workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Performance policy successfully updated.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            _log.error("Unexpected exception configuring tasks for performance policy change: ", ex);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(ex);
            taskCompleter.error(_dbClient, serviceError);
        }
    }
}
