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

import org.eclipse.jetty.util.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.locking.LockTimeoutValue;
import com.emc.storageos.locking.LockType;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.VPlexUtil;
import com.emc.storageos.volumecontroller.BlockExportController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerLockingUtil;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportDeleteCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.ExportUpdateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolAutoTieringPolicyChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeVpoolChangeTaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeWorkflowCompleter;
import com.emc.storageos.workflow.Workflow;
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
        ExportTaskCompleter taskCompleter = new ExportCreateCompleter(export, opId);
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

                _wfUtils.
                        generateExportGroupCreateWorkflow(workflow, null, null,
                                entry.getKey(), export, entry.getValue(), initiatorURIs);
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
     * @param export URI of ExportMask
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
            if (exportGroup.getExportMasks() != null) {
                workflow = _wfUtils.newWorkflow("exportGroupDelete", false, opId);

                Set<URI> storageSystemURIs = new HashSet<URI>();
                // Use temp set to prevent ConcurrentModificationException
                Set<String> tempMaskURIStrSet = new HashSet<String>(exportGroup.getExportMasks());
                for (String maskURIString : tempMaskURIStrSet) {
                    URI exportMaskURI = URI.create(maskURIString);
                    ExportMask exportMask = _dbClient.queryObject(ExportMask.class,
                            exportMaskURI);
                    List<String> lockKeys = ControllerLockingUtil.getHostStorageLockKeys(
                            _dbClient, ExportGroup.ExportGroupType.valueOf(exportGroup.getType()),
                            StringSetUtil.stringSetToUriList(exportGroup.getInitiators()), exportMask.getStorageDevice());
                    boolean acquiredLocks = _wfUtils.getWorkflowService().acquireWorkflowLocks(
                            workflow, lockKeys, LockTimeoutValue.get(LockType.EXPORT_GROUP_OPS));
                    if (!acquiredLocks) {
                        throw DeviceControllerException.exceptions.failedToAcquireLock(lockKeys.toString(),
                                "ExportGroupDelete: " + exportGroup.getLabel());
                    }
                    if (exportMask != null && exportMask.getVolumes() != null) {
                        List<URI> uriList = getExportRemovableObjects(
                                exportGroup, exportMask);
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
                        exportGroup.removeExportMask(exportMaskURI);
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
                Joiner.on(',').join(exportGroup.getVolumes().keySet())));
        _log.info(String.format("Export Mask being analyzed contains block objects: { %s }",
                Joiner.on(',').join(exportMask.getVolumes().keySet())));
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
            URI storage = getExportStorageController(blockObject);
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
            List<URI> updatedClusters, List<URI> updatedHosts,
            List<URI> updatedInitiators, String opId)
            throws ControllerException {
        Map<BlockObjectControllerKey, Map<URI, Integer>> addedStorageToBlockObjects =
                new HashMap<BlockObjectControllerKey, Map<URI, Integer>>();
        Map<BlockObjectControllerKey, Map<URI, Integer>> removedStorageToBlockObjects =
                new HashMap<BlockObjectControllerKey, Map<URI, Integer>>();
        List<URI> addedInitiators = new ArrayList<URI>();
        List<URI> removedInitiators = new ArrayList<URI>();
        List<URI> addedHosts = new ArrayList<URI>();
        List<URI> removedHosts = new ArrayList<URI>();
        List<URI> addedClusters = new ArrayList<URI>();
        List<URI> removedClusters = new ArrayList<URI>();
        Workflow workflow = null;
        try {
            // Do some initial sanitizing of the export parameters
            StringSetUtil.removeDuplicates(updatedClusters);
            StringSetUtil.removeDuplicates(updatedHosts);
            StringSetUtil.removeDuplicates(updatedInitiators);

            computeDiffs(export, 
                    addedBlockObjectMap, removedBlockObjectMap, updatedInitiators,
                    addedStorageToBlockObjects, removedStorageToBlockObjects,
                    addedInitiators, removedInitiators, addedHosts, removedHosts,
                    addedClusters, removedClusters);

            // Generate a flat list of volume/snap objects that will be added
            // to the export update completer so the completer will know what
            // to add upon task completion. We need not carry the block controller
            // into the completer, so we strip that out of the map for the benefit of
            // keeping the completer simple.
            Map<URI, Integer> addedBlockObjects = new HashMap<>();

            for (BlockObjectControllerKey controllerKey : addedStorageToBlockObjects.keySet()) {
                addedBlockObjects.putAll(addedStorageToBlockObjects.get(controllerKey));
            }

            // Generate a flat list of volume/snap objects that will be removed
            // to the export update completer so the completer will know what
            // to remove upon task completion.
            Map<URI, Integer> removedBlockObjects = new HashMap<>();
            for (BlockObjectControllerKey controllerKey : removedStorageToBlockObjects.keySet()) {
                removedBlockObjects.putAll(removedStorageToBlockObjects.get(controllerKey));
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

            // need to iterate over all unique storage controller -> protection controller
            // combinations.

            for (BlockObjectControllerKey controllerKey : addedStorageToBlockObjects.keySet()) {
                _log.info("Creating sub-workflow for storage system {}", String.valueOf(controllerKey.getStorageControllerUri()));
                // TODO: Need to fix, getExportMask() returns a single mask,
                // but there could be more than 1 for a array and ExportGroup
                _wfUtils.
                        generateExportGroupUpdateWorkflow(workflow, null, null,
                                controllerKey.getController(), export, getExportMask(export, controllerKey.getStorageControllerUri()),
                                addedStorageToBlockObjects.get(controllerKey),
                                removedStorageToBlockObjects.get(controllerKey),
                                addedInitiators, removedInitiators, controllerKey.getStorageControllerUri());
            }
            if (!workflow.getAllStepStatus().isEmpty()) {
                _log.info("The updateExportWorkflow has {} steps. Starting the workflow.", workflow.getAllStepStatus().size());
                workflow.executePlan(taskCompleter, "Update the export group on all storage systems successfully.");
            } else {
                taskCompleter.ready(_dbClient);
            }
        } catch (Exception ex) {
            ExportTaskCompleter taskCompleter = new ExportUpdateCompleter(export, opId);
            String message = "exportGroupUpdate caught an exception.";
            _log.error(message, ex);
            if (workflow != null) {
                _wfUtils.getWorkflowService().releaseAllWorkflowLocks(workflow);
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
     *              to be removed.
     * @param newInitiators the updated list of initiators that reflect what needs
     *            to be added and removed from the current list of initiators
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
            List<URI> newInitiators,
            Map<BlockObjectControllerKey, Map<URI, Integer>> addedBlockObjects,
            Map<BlockObjectControllerKey, Map<URI, Integer>> removedBlockObjects,
            List<URI> addedInitiators, List<URI> removedInitiators,
            List<URI> addedHosts, List<URI> removedHosts,
            List<URI> addedClusters, List<URI> removedClusters) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, expoUri);
        Map<URI, Integer> existingMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
        List<URI> existingInitiators = StringSetUtil.stringSetToUriList(exportGroup.getInitiators());
        
        BlockObjectControllerKey controllerKey = null;

        // If there are existing volumes, make sure their controller is represented in the
        // addedBlockObjects and removedBlockObjects maps, even if nothing was added / or removed.
        // This is necessary because the addBlockObjects.keyset() is used by the caller to iterate
        // over the controllers needed for the workflow.
        if (exportGroup.getVolumes() != null) {
            _log.info("Existing export group volumes: " + Joiner.on(',').join(exportGroup.getVolumes().keySet()));
            Map<URI, Integer> existingBlockObjectMap = StringMapUtil.stringMapToVolumeMap(exportGroup.getVolumes());
            for (Map.Entry<URI, Integer> existingBlockObjectEntry : existingBlockObjectMap.entrySet()) {
                BlockObject bo = BlockObject.fetch(_dbClient, existingBlockObjectEntry.getKey());
                URI storageControllerUri = getExportStorageController(bo);
                controllerKey = new BlockObjectControllerKey();
                controllerKey.setStorageControllerUri(bo.getStorageController());
                if (!storageControllerUri.equals(bo.getStorageController())) {
                    controllerKey.setProtectionControllerUri(storageControllerUri);
                }
                Log.info("Existing block object {} in storage {}", bo.getId(), controllerKey.getController());
                // add an entry in each map for the storage system if not already exists
                getOrAddStorageMap(controllerKey, addedBlockObjects);
                getOrAddStorageMap(controllerKey, removedBlockObjects);
            }
        }

        // compute a map of storage-system-to-volumes for volumes to be added
        for (URI uri : addedBlockObjectsFromRequest.keySet()) {
            BlockObject bo = BlockObject.fetch(_dbClient, uri);
            URI storageControllerUri = getExportStorageController(bo);
            
            controllerKey = new BlockObjectControllerKey();
            controllerKey.setStorageControllerUri(bo.getStorageController());
            if (!storageControllerUri.equals(bo.getStorageController())) {
                controllerKey.setProtectionControllerUri(storageControllerUri);
            }
            // add an entry in each map for the storage system if not already exists
            getOrAddStorageMap(controllerKey, addedBlockObjects).put(uri, addedBlockObjectsFromRequest.get(uri));
            getOrAddStorageMap(controllerKey, removedBlockObjects);
            
            _log.info("Block object {} to add to storage: {}",  bo.getId(), controllerKey.getController());
        }
        
        for (URI uri : removedBlockObjectsFromRequest.keySet()) {
            if (existingMap.containsKey(uri)) {
                BlockObject bo = BlockObject.fetch(_dbClient, uri);
                URI storageControllerUri = getExportStorageController(bo);

                controllerKey = new BlockObjectControllerKey();
                controllerKey.setStorageControllerUri(bo.getStorageController());
                if (!storageControllerUri.equals(bo.getStorageController())) {
                    controllerKey.setProtectionControllerUri(storageControllerUri);
                }
                
                // add an empty map for the added blocks so that the two maps have the same keyset
                getOrAddStorageMap(controllerKey,  addedBlockObjects);
                getOrAddStorageMap(controllerKey,  removedBlockObjects).put(uri, existingMap.get(uri));
                
                _log.info("Block object {} to remove from storage: {}", bo.getId(), controllerKey.getController());
            }
        }
        
        // compute the list of initiators to be added and removed
        for (URI uri : newInitiators) {
            if (exportGroup.getInitiators() == null ||
                    !exportGroup.getInitiators().contains(uri.toString())) {
                addedInitiators.add(uri);
            } else {
                existingInitiators.remove(uri);
            }
        }
        removedInitiators.addAll(existingInitiators);
        _log.info("Initiators to add: {}", addedInitiators.toArray());
        _log.info("Initiators to remove: {}", removedInitiators.toArray());

        // If this export group is of type host or cluster, the Host
        // and Cluster IDs in this object are important. Calculate updates to those lists.
        if (exportGroup.forHost() || exportGroup.forCluster()) {
            // Compute the list of hosts to be added and removed.

            // As a foundation for our calculations, first determine what the initiator list
            // will look like at the end of this operation, which is the current initiator list
            // minus the removed ones plus the added ones.
            StringSet updatedInitiatorIds = new StringSet();
            if (exportGroup.getInitiators() != null) {
                updatedInitiatorIds.addAll(exportGroup.getInitiators());
            }
            if (removedInitiators != null) {
                updatedInitiatorIds.removeAll(StringSetUtil.uriListToStringSet(removedInitiators));
            }
            if (addedInitiators != null) {
                updatedInitiatorIds.addAll(StringSetUtil.uriListToStringSet(addedInitiators));
            }

            // Get the initiator objects. We'll need to know the Host object.
            List<Initiator> updatedInitiators = new ArrayList<>();
            if (updatedInitiatorIds != null && !updatedInitiatorIds.isEmpty()) {
                updatedInitiators = _dbClient.queryObject(Initiator.class, StringSetUtil.stringSetToUriList(updatedInitiatorIds));
            }

            // See if any hosts need to be removed.
            // We do this by looping through the hosts in the export group and making sure there's
            // at least one initiator that belongs to that host in the list of initiators when the task is done.
            if (exportGroup.getHosts() != null) {
                for (URI hostId : StringSetUtil.stringSetToUriList(exportGroup.getHosts())) {
                    boolean remove = true;
                    for (Initiator initiator : updatedInitiators) {
                        if (initiator.getHost().equals(hostId)) {
                            remove = false;
                            break;
                        }
                    }
                    if (remove) {
                        removedHosts.add(hostId);
                    }
                }
            }

            // See if any hosts need to be added.
            // We do this by looking at the list of initiators when the task is done and
            // seeing if the hosts associated with those initiators are in the export group.
            // If they are not, we add them to the temporary list for the completer.
            for (Initiator initiator : updatedInitiators) {
                if ((exportGroup.getHosts() == null || !exportGroup.getHosts().contains(initiator.getHost().toString())) &&
                        !addedHosts.contains(initiator.getHost())) {
                    addedHosts.add(initiator.getHost());
                }
            }

            _log.info("Hosts to add: {}", addedHosts.toArray());
            _log.info("Hosts to remove: {}", removedHosts.toArray());

            // In the case of a cluster export group, check for adding/removing clusters
            if (exportGroup.forCluster()) {

                // Similar to initiators in the host block above, we first need a list of hosts
                // that will apply to the export group at the end of the task. We do this by
                // starting with the current export group host list minus the hosts we're removing
                // plus the hosts we're adding as part of this update.
                Set<URI> updatedHostIds = new HashSet<>();
                if (exportGroup.getHosts() != null) {
                    updatedHostIds.addAll(StringSetUtil.stringSetToUriList(exportGroup.getHosts()));
                }
                if (addedHosts != null) {
                    updatedHostIds.addAll(addedHosts);
                }
                if (removedHosts != null) {
                    updatedHostIds.removeAll(removedHosts);
                }

                // Load all of the hosts since we need to examine the cluster ID
                List<Host> updatedHosts = new ArrayList<>();
                if (!updatedHostIds.isEmpty()) {
                    updatedHosts = _dbClient.queryObject(Host.class, updatedHostIds);
                }

                // See if any clusters need to be removed.
                // We do this by looping through the clusters in the export group and making sure there's
                // at least one host that belongs to that cluster in the list of hosts when the task is done.
                if (exportGroup.getClusters() != null) {
                    for (URI clusterId : StringSetUtil.stringSetToUriList(exportGroup.getClusters())) {
                        boolean remove = true;
                        for (Host host : updatedHosts) {
                            if (clusterId.equals(host.getCluster())) {
                                remove = false;
                                break;
                            }
                        }
                        if (remove) {
                            removedClusters.add(clusterId);
                        }
                    }
                }

                // See if any clusters need to be added.
                // We do this by looking at the list of hosts when the task is done and
                // seeing if the clusters associated with those hosts are in the export group.
                // If they are not, we add them to the temporary list for the completer.
                for (Host host : updatedHosts) {
                    if ((exportGroup.getClusters() == null || !exportGroup.getClusters().contains(host.getCluster().toString())) &&
                            !addedClusters.contains(host.getCluster())) {
                        addedClusters.add(host.getCluster());
                    }
                }

                _log.info("Clusters to add: {}", addedClusters.toArray());
                _log.info("Clusters to remove: {}", removedClusters.toArray());
            }
        }

    }

    private Map<URI, Integer> getOrAddStorageMap(BlockObjectControllerKey controllerKey,
            Map<BlockObjectControllerKey, Map<URI, Integer>> map) {
        Map<URI, Integer> volumesForStorage = map.get(controllerKey);
        if (volumesForStorage == null) {
            volumesForStorage = new HashMap<URI, Integer>();
            map.put(controllerKey, volumesForStorage);
        }
        return volumesForStorage;
    }

    private ExportMask getExportMask(URI exportGroupUri, URI storageUri) {
        ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupUri);
        if (exportGroup.getExportMasks() != null) {
            for (String uri : exportGroup.getExportMasks()) {
                try {
                    ExportMask mask = _dbClient.queryObject(ExportMask.class, URI.create(uri));
                    if (mask.getStorageDevice().equals(storageUri)) {
                        return mask;
                    }
                } catch (Exception ex) {
                    // I need to log the fact that I got a bad export mask URI
                    _log.warn("Cannot get export mask for storage " + storageUri + " and export group " + exportGroupUri, ex);
                }
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
            List<Volume>updatedVolumes = new ArrayList<Volume>();
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

            VirtualPool newVpool = _dbClient.queryObject(VirtualPool.class, newVpoolURI);
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
}
