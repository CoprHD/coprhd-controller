/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import static com.emc.storageos.db.client.constraint.ContainmentConstraint.Factory.getVolumesByConsistencyGroup;
import static com.emc.storageos.db.client.util.NullColumnValueGetter.isNullURI;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.api.mapper.BlockMapper;
import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.api.service.impl.resource.BlockServiceApi;
import com.emc.storageos.api.service.impl.resource.utils.BlockServiceUtils;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockConsistencyGroup;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.volumecontroller.BlockController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.smis.ReplicationUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * Abstract full copy implementation provides a container for code and utilities
 * common to all platform specific implementations.
 */
public abstract class AbstractBlockFullCopyApiImpl implements BlockFullCopyApi {

    // A reference to a database client.
    protected DbClient _dbClient;

    // A reference to the coordinator.
    protected CoordinatorClient _coordinator = null;

    // A reference to a scheduler.
    protected Scheduler _scheduler = null;

    // A reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(AbstractBlockFullCopyApiImpl.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator.
     * @param scheduler A reference to the scheduler.
     */
    public AbstractBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator,
            Scheduler scheduler) {
        _dbClient = dbClient;
        _coordinator = coordinator;
        _scheduler = scheduler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        List<BlockObject> fcSourceObjList = new ArrayList<BlockObject>();
        if (URIUtil.isType(fcSourceObj.getId(), BlockSnapshot.class)) {
            // For snapshots we only make a fully copy for the
            // passed snapshot.
            fcSourceObjList.add(fcSourceObj);
        } else {
            // Otherwise, if the volume is in a CG, then we create
            // a full copy for each volume in the CG.
            Volume fcSourceVolume = (Volume) fcSourceObj;
            URI cgURI = fcSourceVolume.getConsistencyGroup();
            if (!isNullURI(cgURI)) {
                BlockConsistencyGroup cg = _dbClient.queryObject(BlockConsistencyGroup.class, cgURI);
                fcSourceObjList.addAll(getActiveCGVolumes(cg));
            } else {
                fcSourceObjList.add(fcSourceObj);
            }
        }

        return fcSourceObjList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<URI, Volume> getFullCopySetMap(BlockObject fcSourceObj,
            Volume fullCopyVolume) {
        Map<URI, Volume> fullCopyMap = new HashMap<URI, Volume>();
        URI cgURI = fcSourceObj.getConsistencyGroup();
        if ((!isNullURI(cgURI))
                && (!BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient))) {
            // If the full copy is not detached and the source is
            // in a CG, then the full copy is treated as a set and
            // there should be a full copy for each source in the
            // CG and they should be part of the same replication
            // group instance.
            URIQueryResultList queryResults = new URIQueryResultList();
            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getCloneReplicationGroupInstanceConstraint(fullCopyVolume
                            .getReplicationGroupInstance()), queryResults);
            Iterator<URI> resultsIter = queryResults.iterator();
            while (resultsIter.hasNext()) {
                URI fullCopyURI = resultsIter.next();
                fullCopyMap.put(fullCopyURI,
                        _dbClient.queryObject(Volume.class, fullCopyURI));
            }
        } else {
            fullCopyMap.put(fullCopyVolume.getId(), fullCopyVolume);
        }
        return fullCopyMap;
    }

    /**
     * Gets the active volumes in the passed consistency group.
     * 
     * @param cg A reference to a consistency group.
     * 
     * @return The active volumes in the passed consistency group.
     */
    protected List<Volume> getActiveCGVolumes(BlockConsistencyGroup cg) {
        List<Volume> volumeList = new ArrayList<Volume>();
        URIQueryResultList uriQueryResultList = new URIQueryResultList();
        _dbClient.queryByConstraint(getVolumesByConsistencyGroup(cg.getId()),
                uriQueryResultList);
        Iterator<Volume> volumeIterator = _dbClient.queryIterativeObjects(Volume.class,
                uriQueryResultList, true);
        while (volumeIterator.hasNext()) {
            Volume volume = volumeIterator.next();
            volumeList.add(volume);
        }
        return volumeList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        // Verify CG volume requested count and CG snapshot
        if (!fcSourceObjList.isEmpty()) {
            BlockObject fcSourceObj = fcSourceObjList.get(0);
            URI cgURI = fcSourceObj.getConsistencyGroup();
            if (!isNullURI(cgURI)) {
                URI fcSourceURI = fcSourceObj.getId();
                if (URIUtil.isType(fcSourceURI, Volume.class)) {
                    verifyCGVolumeRequestCount(count);
                } else {
                    verifyCGSnapshotRequest();
                }
            }
        }

        // Verify full copy is supported for each full copy source object's storage pool.
        for (BlockObject fcSourceObj : fcSourceObjList) {
            // Verify full copy is supported for each full copy
            // source object's storage pool. The pool could be
            // null when called by the VPLEX implementation.
            StoragePool storagePool = BlockFullCopyUtils.queryFullCopySourceStoragePool(fcSourceObj, _dbClient);
            if (storagePool != null) {
                verifyFullCopySupportedForStoragePool(storagePool);
            }

            // Verify the requested copy count.
            verifyFullCopyRequestCount(fcSourceObj, count);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateSnapshotCreateRequest(Volume requestedVolume, List<Volume> volumesToSnap) {
        // Nothing to do by default.
    }

    /**
     * Verify if full copy is supported for the storage pool with the passed
     * URI.
     * 
     * @param storagePool A reference to the storage pool for the full copy
     *            source.
     */
    protected void verifyFullCopySupportedForStoragePool(StoragePool storagePool) {
        StringSet copyTypes = storagePool.getSupportedCopyTypes();
        if ((copyTypes == null) || (!copyTypes.contains(StoragePool.CopyTypes.UNSYNC_UNASSOC.name()))) {
            throw APIException.badRequests.fullCopyNotSupportedOnArray(storagePool.getStorageDevice());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
            String name, boolean createInactive, int count, String taskId) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // If the source is in a CG, then we will activate the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(fcSourceObj, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // The full copy manager will not call activate if the full copy is
        // detached, so if the state is not inactive, then it must have
        // already been activated. In this case return activate action is
        // completed successfully. Otherwise, send activate full copy request
        // to controller.
        if (!BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient)) {
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY);
            op.ready("Full copy is already activated");
            for (URI fullCopyURI : fullCopyURIs) {
                _dbClient.createTaskOpStatus(Volume.class, fullCopyURI, taskId, op);
                TaskResourceRep task = TaskMapper.toTask(fullCopyMap.get(fullCopyURI),
                        taskId, op);
                taskList.addTask(task);
            }
        } else {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    fullCopyVolume.getStorageController());
            BlockController controller = getController(BlockController.class,
                    storageSystem.getSystemType());
            for (URI fullCopyURI : fullCopyURIs) {
                _dbClient.createTaskOpStatus(Volume.class, fullCopyURI, taskId,
                        ResourceOperationTypeEnum.ACTIVATE_VOLUME_FULL_COPY);
            }
            try {
                controller.activateFullCopy(storageSystem.getId(), new ArrayList<URI>(
                        fullCopyURIs), taskId);
            } catch (ControllerException ce) {
                s_logger.error("Failed to activate volume full copy {}", fullCopyVolume.getId(), ce);
                _dbClient.error(Volume.class, fullCopyVolume.getId(), taskId, ce);
            }

            // Get the updated task status
            for (URI fullCopyURI : fullCopyURIs) {
                Volume updatedFullCopyVolume = _dbClient.queryObject(Volume.class, fullCopyURI);
                Operation taskOpStatus = updatedFullCopyVolume.getOpStatus().get(taskId);
                TaskResourceRep task = TaskMapper.toTask(fullCopyMap.get(fullCopyURI),
                        taskId, taskOpStatus);
                taskList.addTask(task);
            }
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {

        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // If the source is in a CG, then we will activate the corresponding
        // full copies for all the volumes in the CG. Since we did not allow
        // full copies for volumes or snaps in CGs prior to Jedi, there should
        // be a full copy for all volumes in the CG.
        Map<URI, Volume> fullCopyMap = getFullCopySetMap(fcSourceObj, fullCopyVolume);
        Set<URI> fullCopyURIs = fullCopyMap.keySet();

        // If full copy volume is already detached, return detach action is
        // completed successfully. Also, if the state is inactive, then it was
        // never activated and therefore is also really detached. Otherwise,
        // send detach full copy request to controller.
        if ((BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) ||
                (BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient))) {
            Operation op = new Operation();
            op.setResourceType(ResourceOperationTypeEnum.DETACH_VOLUME_FULL_COPY);
            op.ready("Full copy is already detached");
            for (URI fullCopyURI : fullCopyURIs) {
                _dbClient.createTaskOpStatus(Volume.class, fullCopyURI, taskId, op);
                TaskResourceRep task = TaskMapper.toTask(fullCopyMap.get(fullCopyURI),
                        taskId, op);
                taskList.addTask(task);
                if (!BlockFullCopyUtils.isFullCopyDetached(fullCopyVolume, _dbClient)) {
                    // Make sure the replica state is set to detached.
                    Volume volume = fullCopyVolume;
                    if (!fullCopyURI.equals(fullCopyVolume.getId())) {
                        volume = _dbClient.queryObject(Volume.class, fullCopyURI);
                    }
                    ReplicationUtils.removeDetachedFullCopyFromSourceFullCopiesList(volume, _dbClient);
                    volume.setAssociatedSourceVolume(NullColumnValueGetter.getNullURI());
                    volume.setReplicaState(ReplicationState.DETACHED.name());
                    _dbClient.persistObject(volume);
                }
            }
        } else {
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    fullCopyVolume.getStorageController());
            BlockController controller = getController(BlockController.class,
                    storageSystem.getSystemType());
            for (URI fullCopyURI : fullCopyURIs) {
                _dbClient.createTaskOpStatus(Volume.class, fullCopyURI, taskId,
                        ResourceOperationTypeEnum.DETACH_VOLUME_FULL_COPY);
            }
            try {
                controller.detachFullCopy(storageSystem.getId(), new ArrayList<URI>(
                        fullCopyURIs), taskId);
            } catch (ControllerException ce) {
                s_logger.error("Failed to detach volume full copy {}", fullCopyVolume.getId(), ce);
                _dbClient.error(Volume.class, fullCopyVolume.getId(), taskId, ce);
            }

            // Get the updated task status
            for (URI fullCopyURI : fullCopyURIs) {
                Volume updatedFullCopyVolume = _dbClient.queryObject(Volume.class, fullCopyURI);
                Operation taskOpStatus = updatedFullCopyVolume.getOpStatus().get(taskId);
                TaskResourceRep task = TaskMapper.toTask(fullCopyMap.get(fullCopyURI),
                        taskId, taskOpStatus);
                taskList.addTask(task);
            }
        }

        addConsistencyGroupTasks(Arrays.asList(fcSourceObj), taskList, taskId,
                ResourceOperationTypeEnum.DETACH_CONSISTENCY_GROUP_FULL_COPY);

        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList establishVolumeAndFullCopyGroupRelation(Volume sourceVolume, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupported();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        Integer result = getSyncPercentage(sourceURI, fullCopyVolume);
        VolumeRestRep volumeRestRep = BlockMapper.map(_dbClient, fullCopyVolume);
        volumeRestRep.getProtection().getFullCopyRep().setPercentSynced(result);
        return volumeRestRep;
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean volumeCanBeDeleted(Volume volume) {
        /**
         * Delete volume api call will delete all its related replicas for VMAX using SMI 8.0.3.
         * Hence vmax using 8.0.3 can be delete even if volume has replicas.
         */
        if (volume.isInCG() && BlockServiceUtils.checkVolumeCanBeAddedOrRemoved(volume, _dbClient)) {
            return true;
        }

        boolean volumeCanBeDeleted = true;

        // Verify that a volume that is a full copy is detached.
        if ((BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) &&
                (!BlockFullCopyUtils.isFullCopyDetached(volume, _dbClient))) {
            volumeCanBeDeleted = false;
        }

        // Verify that a volume that is a full copy source is detached
        // from those full copies.
        if ((volumeCanBeDeleted) && (BlockFullCopyUtils.isVolumeFullCopySource(volume, _dbClient))) {
            volumeCanBeDeleted = BlockFullCopyUtils.volumeDetachedFromFullCopies(volume, _dbClient);
        }

        return volumeCanBeDeleted;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean volumeCanBeExpanded(Volume volume) {
        if (((BlockFullCopyUtils.isVolumeFullCopy(volume, _dbClient)) &&
                (!BlockFullCopyUtils.isFullCopyDetached(volume, _dbClient))) ||
                ((BlockFullCopyUtils.isVolumeFullCopySource(volume, _dbClient)) &&
                (!BlockFullCopyUtils.volumeDetachedFromFullCopies(volume, _dbClient)))) {
            return false;
        }

        return true;
    }

    /**
     * Gets the percent synchronized for the passed full copy volume.
     * 
     * @param sourceURI The URI of the full copy source.
     * @param fullCopyVolume A reference to the full copy volume.
     * 
     * @return The percent synchronized.
     */
    protected Integer getSyncPercentage(URI sourceURI, Volume fullCopyVolume) {
        Integer result = null;
        URI fullCopyURI = fullCopyVolume.getId();
        if (!BlockFullCopyUtils.isFullCopyInactive(fullCopyVolume, _dbClient)) {
            String taskId = UUID.randomUUID().toString();
            StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class,
                    fullCopyVolume.getStorageController());
            BlockController controller = getController(BlockController.class,
                    storageSystem.getSystemType());
            try {
                result = controller.checkSyncProgress(storageSystem.getId(),
                        sourceURI, fullCopyURI, taskId);
            } catch (ControllerException ce) {
                s_logger.error("Failed to check synchronization progress for volume full copy {}",
                        fullCopyURI, ce);
            }
        } else {
            result = 0;
        }

        if (result == null) {
            throw APIException.badRequests.protectionUnableToGetSynchronizationProgress();
        }

        return result;
    }

    /**
     * Looks up controller dependency for given hardware
     * 
     * @param clazz controller interface
     * @param hw hardware name
     * @param <T>
     * 
     * @return
     */
    protected <T extends Controller> T getController(Class<T> clazz, String hw) {
        return _coordinator.locateService(clazz, BlockServiceApi.CONTROLLER_SVC,
                BlockServiceApi.CONTROLLER_SVC_VER, hw, clazz.getSimpleName());
    }

    /**
     * Creates a capabilities wrapper specifying some parameters for the
     * full copy create request.
     * 
     * @param fcSourceObj A reference to the full copy source.
     * @param vpool A reference to the source's virtual pool.
     * @param count A count of the number of full copies requested.
     * 
     * @return VirtualPoolCapabilityValuesWrapper
     */
    protected VirtualPoolCapabilityValuesWrapper getCapabilitiesForFullCopyCreate(
            BlockObject fcSourceObj, VirtualPool vpool, int count) {
        VirtualPoolCapabilityValuesWrapper capabilities = new VirtualPoolCapabilityValuesWrapper();
        capabilities.put(VirtualPoolCapabilityValuesWrapper.RESOURCE_COUNT, count);
        capabilities.put(VirtualPoolCapabilityValuesWrapper.SIZE,
                BlockFullCopyUtils.getCapacityForFullCopySource(fcSourceObj, _dbClient));
        if (VirtualPool.ProvisioningType.Thin.toString().equalsIgnoreCase(
                vpool.getSupportedProvisioningType())) {
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_PROVISIONING, Boolean.TRUE);

            // To guarantee that storage pool for a copy has enough physical
            // space to contain current allocated capacity of thin source volume
            capabilities.put(VirtualPoolCapabilityValuesWrapper.THIN_VOLUME_PRE_ALLOCATE_SIZE,
                    BlockFullCopyUtils.getAllocatedCapacityForFullCopySource(fcSourceObj, _dbClient));
        }

        return capabilities;
    }

    /**
     * Sorts the passed list of full copy source objects based
     * on the natural sort order of their labels. Used to align
     * the labels for the full copies with their sources. For example
     * say you have a CG with two volumes foo-1 and foo-2, and you
     * then create a full copy of one of these volumes. Because
     * they are in a CG, a full copy is created for each volume
     * in the CG. When we create the full copies with a name of
     * bar, then we want the full copy for foo-1 to be bar-1, and
     * the full copy for foo-2 to be bar-2. However, when you get
     * the volumes in the CG, there is no guarantee of order. Now
     * there is no restriction that volumes in a CG are named like
     * this, but often they are because often one would create
     * multiple volumes in a CG in a single request. So, in this
     * simple case, then the full copy names should align. We can
     * always create a more sophisticated comparator for the sort
     * routine if something more elaborate is desired.
     * 
     * @param fcSourceObjects A list of full copy source objects.
     * 
     * @return The objects sorted in natural order by their labels.
     */
    protected List<BlockObject> sortFullCopySourceList(List<BlockObject> fcSourceObjects) {
        List<BlockObject> sortedSourceObjects = new ArrayList<BlockObject>();

        // Put the full copy source objects in a map
        // keyed by label.
        Map<String, BlockObject> fcSourcObjectsMap = new HashMap<String, BlockObject>();
        for (BlockObject fcSourceObject : fcSourceObjects) {
            fcSourcObjectsMap.put(fcSourceObject.getLabel(), fcSourceObject);
        }

        // Create a list of the labels and sort them in natural order.
        List<String> fcSourceLabels = new ArrayList<String>(fcSourcObjectsMap.keySet());
        Collections.sort(fcSourceLabels);

        // Iterate over the sorted labels adding them to the list.
        for (String fcSourceLabel : fcSourceLabels) {
            sortedSourceObjects.add(fcSourcObjectsMap.get(fcSourceLabel));
        }
        return sortedSourceObjects;
    }

    /**
     * Verify the requested full copy count is valid.
     * 
     * @param fcSourceObj A reference to the full copy source.
     * @param count The requested full copy count.
     */
    protected void verifyFullCopyRequestCount(BlockObject fcSourceObj, int count) {
        BlockFullCopyUtils.validateActiveFullCopyCount(fcSourceObj, count, _dbClient);
    }

    /**
     * Verify the requested full copy count for volume in CG is valid.
     * For array where group clone is not supported, override this method in platform specific impl.
     * 
     * @param count The requested full copy count.
     */
    protected void verifyCGVolumeRequestCount(int count) {
        // We only allow you to create a single full copy at a time
        // for volumes in a consistency group if group clone is supported.
        if (count > 1) {
            throw APIException.badRequests.invalidFullCopyCountForVolumesInConsistencyGroup();
        }
    }

    /**
     * Verify the requested full copy for snapshot of volume in CG.
     * For array where group clone is not supported, override this method in platform specific impl.
     */
    protected void verifyCGSnapshotRequest() {
        // We don't support creating full copies of snapshots in consistency groups.
        throw APIException.badRequests.fullCopyNotSupportedForConsistencyGroup();
    }

    /**
     * Creates tasks against consistency groups associated with a request and adds them to the given task list.
     *
     * @param objects
     * @param taskList
     * @param taskId
     * @param operationTypeEnum
     * @param <T>
     */
    protected <T extends BlockObject> void addConsistencyGroupTasks(List<T> objects, TaskList taskList, String taskId,
                                                                  ResourceOperationTypeEnum operationTypeEnum) {
        Set<URI> consistencyGroups = new HashSet<>();
        for (T object : objects) {
            if (!isNullURI(object.getConsistencyGroup())) {
                consistencyGroups.add(object.getConsistencyGroup());
            }
        }

        if (consistencyGroups.isEmpty()) {
            return;
        }

        List<BlockConsistencyGroup> groups = _dbClient.queryObject(BlockConsistencyGroup.class, consistencyGroups);
        for (BlockConsistencyGroup group : groups) {
            Operation op = _dbClient.createTaskOpStatus(BlockConsistencyGroup.class, group.getId(), taskId,
                    operationTypeEnum);
            taskList.getTaskList().add(TaskMapper.toTask(group, taskId, op));
        }
    }
}
