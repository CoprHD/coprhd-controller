/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

/**
 * A HDS Volume Delete job
 */
public class HDSDeleteVolumeJob extends HDSJob
{
    private static final Logger _log = LoggerFactory.getLogger(HDSDeleteVolumeJob.class);

    public HDSDeleteVolumeJob(String hdsJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(hdsJob, storageSystem, taskCompleter, "DeleteVolume");
    }

    public HDSDeleteVolumeJob(String hdsJob,
            URI storageSystem,
            TaskCompleter taskCompleter, String name) {
        super(hdsJob, storageSystem, taskCompleter, name);
    }

    /**
     * Called to update the job status when the volume delete job completes.
     * 
     * @param jobContext The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        DbClient dbClient = jobContext.getDbClient();
        try {
            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                    getStorageSystemURI());

            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());

            // Get list of volumes; get set of storage pool ids to which they
            // belong.
            List<Volume> volumes = new ArrayList<Volume>();
            Set<URI> poolURIs = new HashSet<URI>();
            for (URI id : getTaskCompleter().getIds()) {
                // Volume volume = dbClient.queryObject(Volume.class, id);
                Volume volume = (Volume) BlockObject.fetch(dbClient, id);
                if (volume != null && !volume.getInactive()) {
                    volumes.add(volume);
                    poolURIs.add(volume.getPool());
                }
            }

            // If terminal state update storage pool capacity
            if (_status == JobStatus.SUCCESS || _status == JobStatus.FAILED) {
                // Update capacity of storage pools.
                for (URI poolURI : poolURIs) {
                    StoragePool storagePool = dbClient.queryObject(StoragePool.class,
                            poolURI);
                    HDSUtils.updateStoragePoolCapacity(dbClient, hdsApiClient,
                            storagePool);
                }
            }

            StringBuilder logMsgBuilder = new StringBuilder();
            if (_status == JobStatus.SUCCESS) {
                for (Volume volume : volumes) {
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Successfully deleted volume %s",
                            volume.getId()));
                }

            } else if (_status == JobStatus.FAILED) {
                for (URI id : getTaskCompleter().getIds()) {
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder
                            .append(String.format("Failed to delete volume: %s", id));
                }
            }
            if (logMsgBuilder.length() > 0) {
                _log.info(logMsgBuilder.toString());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error during delete volume job status processing: " + e.getMessage());
            _log.error(
                    "Caught exception while handling updateStatus for delete volume job.",
                    e);
        } finally {
            super.updateStatus(jobContext);
        }
    }
}