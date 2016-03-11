/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.api.service.impl.placement.StorageScheduler;
import com.emc.storageos.api.service.impl.placement.VolumeRecommendation;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.VirtualPool;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.VolumeGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.protectioncontroller.impl.recoverpoint.RPHelper;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;

/**
 * The RecoverPoint storage system implementation for the block full copy API.
 */
public class RPBlockFullCopyApiImpl extends AbstractBlockFullCopyApiImpl {

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public RPBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler, BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(fcSourceObj);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.getAllSourceObjectsForFullCopyRequest(fcSourceObj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        BlockFullCopyApi fullCopyApiImpl = null;
        List<BlockObject> vplexList = new ArrayList<BlockObject>();
        List<BlockObject> blockList = new ArrayList<BlockObject>();
        if (fcSourceObjList != null && !fcSourceObjList.isEmpty()) {
            // Sort the volume list for vplex and non-vplex volumes
            for (BlockObject src : fcSourceObjList) {
                URI storageUri = src.getStorageController();
                StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageUri);
                if (storage.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vplex.toString())) {
                    vplexList.add(src);
                } else {
                   blockList.add(src);
                }
            }
        } else {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        if (!vplexList.isEmpty()) {
            fullCopyApiImpl = _fullCopyMgr.getVplexFullCopyImpl();
            fullCopyApiImpl.validateFullCopyCreateRequest(vplexList, count);
        }
        if (!blockList.isEmpty()) {
            BlockObject block = blockList.get(0);
            StorageSystem system = _dbClient.queryObject(StorageSystem.class, block.getStorageController());
            fullCopyApiImpl = _fullCopyMgr.getPlatformSpecificFullCopyImplForSystem(system);
            fullCopyApiImpl.validateFullCopyCreateRequest(blockList, count);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray,
            String name, boolean createInactive, int count, String taskId) {
        BlockFullCopyApi fullCopyApiImpl = null;
        TaskList taskList = new TaskList();
        List<BlockObject> vplexList = new ArrayList<BlockObject>();
        List<BlockObject> blockList = new ArrayList<BlockObject>();
        if (fcSourceObjList != null && !fcSourceObjList.isEmpty()) {
            // Sort the volume list for vplex and non-vplex volumes
            for (BlockObject src : fcSourceObjList) {
                URI storageUri = src.getStorageController();
                StorageSystem storage = _dbClient.queryObject(StorageSystem.class, storageUri);
                if (storage.getSystemType().equalsIgnoreCase(DiscoveredDataObject.Type.vplex.toString())) {
                    vplexList.add(src);
                } else {
                   blockList.add(src);
                }
            }
        } else {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        if (!vplexList.isEmpty()) {
            fullCopyApiImpl = _fullCopyMgr.getVplexFullCopyImpl();
            taskList.getTaskList().addAll(fullCopyApiImpl.create(vplexList, varray, name, createInactive, count, taskId).getTaskList());
        }
        if (!blockList.isEmpty()) {
            BlockObject block = blockList.get(0);
            if (block instanceof Volume
                    && RPHelper.isProtectionBasedSnapshot((Volume) block, BlockSnapshot.TechnologyType.RP.name(), _dbClient)) {
                taskList.getTaskList()
                        .addAll(prepareAndCreateForBlock(blockList, varray, name, createInactive, count, taskId).getTaskList());
            } else {
                StorageSystem system = _dbClient.queryObject(StorageSystem.class, block.getStorageController());
                fullCopyApiImpl = _fullCopyMgr.getPlatformSpecificFullCopyImplForSystem(system);
                taskList.getTaskList().addAll(fullCopyApiImpl.create(blockList, varray, name, createInactive, count, taskId).getTaskList());
            }
        }
        return taskList;
    }

    private TaskList prepareAndCreateForBlock(List<BlockObject> fcSourceObjList, VirtualArray varray, String name, boolean createInactive,
            int count, String taskId) {
        // Get the placement recommendations for the full copies and
        // prepare the ViPR volumes to represent the full copies.
        // TBD We are getting recommendations one at a time instead
        // of for all full copies at the same time as was done
        // previously. However, now we are allowing for creating
        // full copies for multiple volume form a CG. These volumes
        // could have different vpools and sizes. Therefore, I don't
        // see how we can get them at the same time for all volumes
        // as the capabilities could be different. I guess the
        // possible result is that if the volumes are the same, they
        // could be placed in the same storage pool and if the pool
        // is approaching capacity, there may not actually be enough
        // space in the recommended pool.
        int sourceCounter = 0;
        List<Volume> volumesList = new ArrayList<Volume>();
        BlockObject aFCSource = null;
        Map<URI, VirtualArray> vArrayCache = new HashMap<URI, VirtualArray>();
        List<BlockObject> sortedSourceObjectList = sortFullCopySourceList(fcSourceObjList);
        for (BlockObject fcSourceObj : sortedSourceObjectList) {
            // Make sure when there are multiple source objects,
            // each full copy has a unique name.
            aFCSource = fcSourceObj;
            // volumes in VolumeGroup can be from different vArrays
            varray = getVarrayFromCache(vArrayCache, fcSourceObj.getVirtualArray());
            String copyName = null;
            if (NullColumnValueGetter.isNotNullValue(fcSourceObj.getReplicationGroupInstance())) {
                copyName = name + "-" + fcSourceObj.getReplicationGroupInstance()
                        + (sortedSourceObjectList.size() > 1 ? "-" + ++sourceCounter : "");
            } else {
                copyName = name + (sortedSourceObjectList.size() > 1 ? "-" + ++sourceCounter : "");
            }

            VirtualPool vpool = BlockFullCopyUtils.queryFullCopySourceVPool(fcSourceObj, _dbClient);
            VirtualPoolCapabilityValuesWrapper capabilities = getCapabilitiesForFullCopyCreate(fcSourceObj, vpool, count);
            List<VolumeRecommendation> placementRecommendations = getPlacementRecommendations(fcSourceObj, capabilities, varray,
                    vpool.getId());
            volumesList.addAll(prepareClonesForEachRecommendation(copyName, name, fcSourceObj, capabilities, createInactive,
                    placementRecommendations));
        }

        // Invoke the controller.
        return invokeFullCopyCreate(aFCSource, volumesList, createInactive, taskId);
    }

    private List<Volume> prepareClonesForEachRecommendation(String name, String cloneSetName, BlockObject blockObject,
            VirtualPoolCapabilityValuesWrapper capabilities, Boolean createInactive, List<VolumeRecommendation> placementRecommendations) {

        // Prepare clones for each recommendation
        List<Volume> volumesList = new ArrayList<Volume>();
        List<Volume> toUpdate = new ArrayList<Volume>();
        boolean inApplication = false;
        if (blockObject instanceof Volume && ((Volume) blockObject).getApplication(_dbClient) != null) {
            inApplication = true;
        }
        int volumeCounter = (capabilities.getResourceCount() > 1) ? 1 : 0;
        for (VolumeRecommendation recommendation : placementRecommendations) {

            Volume volume = StorageScheduler.prepareFullCopyVolume(_dbClient, name, blockObject, recommendation, volumeCounter,
                    capabilities, createInactive);
            // For Application, set the user provided clone name on all the clones to identify clone set
            if (inApplication) {
                volume.setFullCopySetName(cloneSetName);
                toUpdate.add(volume);
            }
            volumesList.add(volume);
            // set volume Id in the recommendation
            recommendation.setId(volume.getId());
            volumeCounter++;
        }
        // persist changes
        if (!toUpdate.isEmpty()) {
            _dbClient.updateObject(toUpdate);
        }
        return volumesList;
    }

    private List<VolumeRecommendation> getPlacementRecommendations(BlockObject blockObject, VirtualPoolCapabilityValuesWrapper capabilities,
            VirtualArray varray, URI vpoolURI) {

        // Find placement for block volume copies
        VirtualPool vPool = _dbClient.queryObject(VirtualPool.class, vpoolURI);
        List<VolumeRecommendation> placementRecommendations = _fullCopyMgr.getBlockScheduler().getRecommendationsForVolumeClones(varray,
                vPool, blockObject, capabilities);
        if (placementRecommendations.isEmpty()) {
            throw APIException.badRequests.invalidParameterNoStorageFoundForVolume(varray.getId(), vPool.getId(), blockObject.getId());
        }
        return placementRecommendations;
    }

    /**
     * Invokes the controller to create the full copy volumes.
     * 
     * @param fcSourceObj
     *            A reference to a full copy source.
     * @param fullCopyVolumes
     *            A list of the prepared full copy volumes.
     * @param createInactive
     *            true to create the full copies inactive, false otherwise.
     * @param taskId
     *            The unique task identifier
     * 
     * @return TaskList
     */
    private TaskList invokeFullCopyCreate(BlockObject fcSourceObj, List<Volume> fullCopyVolumes, Boolean createInactive, String taskId) {

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, fcSourceObj.getStorageController());
        ProtectionSystem protectionSystem = _dbClient.queryObject(ProtectionSystem.class, fcSourceObj.getProtectionController());

        TaskList taskList = new TaskList();

        List<URI> fullCopyIds = new ArrayList<URI>();
        for (Volume volume : fullCopyVolumes) {
            Operation op = _dbClient.createTaskOpStatus(Volume.class, volume.getId(), taskId,
                    ResourceOperationTypeEnum.CREATE_VOLUME_FULL_COPY);
            volume.getOpStatus().put(taskId, op);
            TaskResourceRep volumeTask = TaskMapper.toTask(volume, taskId, op);
            taskList.getTaskList().add(volumeTask);
            fullCopyIds.add(volume.getId());
        }

        // if Volume is part of Application (COPY type VolumeGroup)
        VolumeGroup volumeGroup = (fcSourceObj instanceof Volume) ? ((Volume) fcSourceObj).getApplication(_dbClient) : null;
        if (volumeGroup != null && !ControllerUtils.checkVolumeForVolumeGroupPartialRequest(_dbClient, (Volume) fcSourceObj)) {

            Operation op = _dbClient.createTaskOpStatus(VolumeGroup.class, volumeGroup.getId(), taskId,
                    ResourceOperationTypeEnum.CREATE_VOLUME_GROUP_FULL_COPY);
            taskList.getTaskList().add(TaskMapper.toTask(volumeGroup, taskId, op));

            // get all volumes to create tasks for all CGs involved
            List<Volume> volumes = ControllerUtils.getVolumeGroupVolumes(_dbClient, volumeGroup);
            addConsistencyGroupTasks(volumes, taskList, taskId, ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_FULL_COPY);
        } else {
            addConsistencyGroupTasks(Arrays.asList(fcSourceObj), taskList, taskId,
                    ResourceOperationTypeEnum.CREATE_CONSISTENCY_GROUP_FULL_COPY);
        }

        try {
            RPController controller = getController(RPController.class, protectionSystem.getSystemType());
            controller.createFullCopy(protectionSystem.getId(), storageSystem.getId(), fullCopyIds, createInactive, taskId);
        } catch (InternalException ie) {
            handleFailedRequest(taskId, taskList, fullCopyVolumes, ie, true);
        }
        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(fcSourceObj);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(fcSourceObj);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.detach(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(sourceVolume);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.restoreSource(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        BlockFullCopyApi fullCopyApiImpl = getFullCopyApiImpl(sourceVolume);
        if (fullCopyApiImpl == null) {
            throw APIException.methodNotAllowed.notSupportedForRP();
        }
        return fullCopyApiImpl.resynchronizeCopy(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        throw APIException.methodNotAllowed.notSupportedForRP();
    }

    /**
     * get the full copy implementation based on the block object
     * 
     * @param fcSourceObj block object
     * @return
     */
    private BlockFullCopyApi getFullCopyApiImpl(BlockObject fcSourceObj) {
        BlockFullCopyApi fullCopyApiImpl = null;
        if (!NullColumnValueGetter.isNullURI(fcSourceObj.getStorageController())) {
            StorageSystem storage = _dbClient.queryObject(StorageSystem.class, fcSourceObj.getStorageController());
            if (storage.getSystemType().equals(DiscoveredDataObject.Type.vplex.name())) {
                fullCopyApiImpl = _fullCopyMgr.getVplexFullCopyImpl();
            } else {
                fullCopyApiImpl = _fullCopyMgr.getPlatformSpecificFullCopyImplForSystem(storage);
            }
        }
        return fullCopyApiImpl;
    }
}
