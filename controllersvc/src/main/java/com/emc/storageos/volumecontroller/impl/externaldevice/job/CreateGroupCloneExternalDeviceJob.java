/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalBlockStorageDevice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.task.CreateGroupCloneDriverTask;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUtils;

/**
 * This ExternalDeviceJob derived class is created to monitor the progress
 * of a request to a create group clone that will complete asynchronously.
 */
public class CreateGroupCloneExternalDeviceJob extends ExternalDeviceJob {
    
    private static final long serialVersionUID = 1L;
    
    // The URI of the volume serving as the clone.
    private List<URI> _volumeURIs;
    
    // The URI of the consistency group
    private URI _cgURI;

    // Logger reference.
    private static final Logger s_logger = LoggerFactory.getLogger(CreateGroupCloneExternalDeviceJob.class);

    /**
     * Constructor.
     * 
     * @param storageSystemURI The URI of the external storage system on which the task is running.
     * @param volumeURIs The URI of the volume serving as the clone.
     * @param cgURI The consistency group URI.
     * @param driverTaskId The id of the task monitored by the job.
     * @param taskCompleter The task completer.
     */
    public CreateGroupCloneExternalDeviceJob(URI storageSystemURI, List<URI> volumeURIs, URI cgURI,
            String driverTaskId, TaskCompleter taskCompleter) {
        super(storageSystemURI, driverTaskId, taskCompleter);
        _volumeURIs = volumeURIs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskSucceeded(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.info(String.format("Successfully created group clone: %s", driverTask.getMessage()));
        
        // Update the ViPR volumes representing the clone with the
        // corresponding driver clone.
        List<Volume> updatedVolumes = new ArrayList<>();
        for (URI volumeURI : _volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                s_logger.error(String.format("Failed to find volume %s", volumeURI));
                throw DeviceControllerException.exceptions.objectNotFound(volumeURI);
            }
            
            // Update the ViPR clone with the driver clone information.
            CreateGroupCloneDriverTask createCloneDriverTask = (CreateGroupCloneDriverTask) driverTask;
            List<VolumeClone> updatedClones = createCloneDriverTask.getClones();
            for (VolumeClone updatedClone: updatedClones) {
                if (ExternalDeviceUtils.isVolumeExternalDeviceClone(volume, updatedClone, dbClient)) {
                    ExternalDeviceUtils.updateNewlyCreatedGroupClone(volume, updatedClone, _cgURI, dbClient);
                    updatedVolumes.add(volume);
                    break;
                }
            }
        }
        dbClient.updateObject(updatedVolumes);

        try {
            // post process storage pool capacity for clone's pools
            // map clones to their storage pool
            updateStoragePoolCapacityAfterOperationComplete(updatedVolumes, dbClient);
        } catch (Exception ex) {
            s_logger.error("Failed to update storage pool after create group clone operation completion.", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doTaskFailed(DriverTask driverTask, DbClient dbClient) throws Exception {
        s_logger.error(String.format("Failed to create group volume clone: %s", driverTask.getMessage()));
        
        List<Volume> volumes = new ArrayList<>();
        for (URI volumeURI : _volumeURIs) {
            Volume volume = dbClient.queryObject(Volume.class, volumeURI);
            if (volume == null) {
                s_logger.error(String.format("Failed to find volume %s", volumeURI));
            } else {
                volume.setInactive(true);
                volumes.add(volume);
            }
        }
        dbClient.updateObject(volumes);

        try {
            // post process storage pool capacity for clone's pools
            // map clones to their storage pool
            updateStoragePoolCapacityAfterOperationComplete(volumes, dbClient);
        } catch (Exception ex) {
            s_logger.error("Failed to update storage pool after create group clone operation completion.", ex);
        }
    }

    /**
     * Updates capacity of storage pools used for clones.
     *
     * @param clones list of clones
     * @param dbClient db client
     */
    private void updateStoragePoolCapacityAfterOperationComplete(List<Volume> clones, DbClient dbClient) {
        Map<URI, List<URI>> dbPoolToClone = new HashMap<>();
        for (Volume clone : clones) {
            URI dbPoolUri = clone.getPool();
            List<URI> poolClones = dbPoolToClone.get(dbPoolUri);
            if (poolClones == null) {
                poolClones = new ArrayList<>();
                dbPoolToClone.put(dbPoolUri, poolClones);
            }
            poolClones.add(clone.getId());
        }
        StorageSystem dbSystem = dbClient.queryObject(StorageSystem.class, _storageSystemURI);
        for (URI dbPoolUri : dbPoolToClone.keySet()) {
            StoragePool dbPool = dbClient.queryObject(StoragePool.class, dbPoolUri);
            ExternalBlockStorageDevice.updateStoragePoolCapacity(dbPool, dbSystem,
                    dbPoolToClone.get(dbPoolUri), dbClient);
        }
    }
}