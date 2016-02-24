/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.api.service.impl.resource.fullcopy;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.api.mapper.TaskMapper;
import com.emc.storageos.api.service.impl.placement.Scheduler;
import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.BlockSnapshot;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.model.TaskList;
import com.emc.storageos.model.TaskResourceRep;
import com.emc.storageos.model.block.VolumeRestRep;
import com.emc.storageos.svcs.errorhandling.resources.APIException;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.BlockController;

/**
 * The VMAX storage system implementation for the block full copy API.
 */
public class VMAXBlockFullCopyApiImpl extends DefaultBlockFullCopyApiImpl {

    private static final Logger s_logger = LoggerFactory.getLogger(VMAXBlockFullCopyApiImpl.class);

    /**
     * Constructor
     * 
     * @param dbClient A reference to a database client.
     * @param coordinator A reference to the coordinator client.
     * @param scheduler A reference to a scheduler.
     * @param fullCopyMgr A reference to the full copy manager.
     */
    public VMAXBlockFullCopyApiImpl(DbClient dbClient, CoordinatorClient coordinator, Scheduler scheduler, BlockFullCopyManager fullCopyMgr) {
        super(dbClient, coordinator, scheduler, fullCopyMgr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BlockObject> getAllSourceObjectsForFullCopyRequest(BlockObject fcSourceObj) {
        return super.getAllSourceObjectsForFullCopyRequest(fcSourceObj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validateFullCopyCreateRequest(List<BlockObject> fcSourceObjList, int count) {
        if (!fcSourceObjList.isEmpty()) {
            URI fcSourceObjURI = fcSourceObjList.get(0).getId();
            if (URIUtil.isType(fcSourceObjURI, BlockSnapshot.class)) {
                // Not supported for snapshots.
                throw APIException.badRequests.fullCopyNotSupportedFromSnapshot(
                        DiscoveredDataObject.Type.vmax.name(), fcSourceObjURI);
            } else {
                super.validateFullCopyCreateRequest(fcSourceObjList, count);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList create(List<BlockObject> fcSourceObjList, VirtualArray varray, String name, boolean createInactive, int count,
            boolean copySource, String taskId) {
        return super.create(fcSourceObjList, varray, name, createInactive, count, copySource, taskId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList activate(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.activate(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList detach(BlockObject fcSourceObj, Volume fullCopyVolume) {
        return super.detach(fcSourceObj, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList restoreSource(Volume sourceVolume, Volume fullCopyVolume) {
        return super.restoreSource(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList resynchronizeCopy(Volume sourceVolume, Volume fullCopyVolume) {
        return super.resynchronizeCopy(sourceVolume, fullCopyVolume);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TaskList establishVolumeAndFullCopyGroupRelation(Volume sourceVolume, Volume fullCopyVolume) {

        // Create the task list.
        TaskList taskList = new TaskList();

        // Create a unique task id.
        String taskId = UUID.randomUUID().toString();

        // Get the id of the source volume.
        URI sourceVolumeURI = sourceVolume.getId();

        // Get the id of the full copy volume.
        URI fullCopyURI = fullCopyVolume.getId();

        // Get the storage system for the source volume.
        StorageSystem sourceSystem = _dbClient.queryObject(StorageSystem.class,
                sourceVolume.getStorageController());
        URI sourceSystemURI = sourceSystem.getId();

        // Create the task on the full copy volume.
        Operation op = _dbClient.createTaskOpStatus(Volume.class, fullCopyURI,
                taskId, ResourceOperationTypeEnum.ESTABLISH_VOLUME_FULL_COPY);
        fullCopyVolume.getOpStatus().put(taskId, op);
        TaskResourceRep fullCopyVolumeTask = TaskMapper.toTask(
                fullCopyVolume, taskId, op);
        taskList.getTaskList().add(fullCopyVolumeTask);

        // Invoke the controller.
        try {
            BlockController controller = getController(BlockController.class,
                    sourceSystem.getSystemType());
            controller.establishVolumeAndFullCopyGroupRelation(sourceSystemURI, sourceVolumeURI,
                    fullCopyURI, taskId);
        } catch (InternalException ie) {
            s_logger.error(String.format("Failed to establish group relation between volume group"
                    + " and full copy group. Volume: %s, Full copy: %s",
                    sourceVolumeURI, fullCopyVolume.getId()), ie);
            super.handleFailedRequest(taskId, taskList,
                    Arrays.asList(fullCopyVolume), ie, false);
        }

        return taskList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public VolumeRestRep checkProgress(URI sourceURI, Volume fullCopyVolume) {
        return super.checkProgress(sourceURI, fullCopyVolume);
    }
}
