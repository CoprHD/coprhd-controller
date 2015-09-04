/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) 2014. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.HDSMirrorOperations;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                volumes.add(volume);
                poolURIs.add(volume.getPool());
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
                super.updateStatus(jobContext);
                for (Volume volume : volumes) {
                    /*
                     * if (URIUtil.isType(volume.getId(), BlockMirror.class)) {
                     * BlockMirror mirror = (BlockMirror) volume;
                     * HDSMirrorOperations.removeReferenceFromSourceVolume(dbClient, mirror);
                     * }
                     */
                    volume.setInactive(true);
                    dbClient.persistObject(volume);
                    dbClient.updateTaskOpStatus(
                            Volume.class,
                            volume.getId(),
                            getTaskCompleter().getOpId(),
                            new Operation(Operation.Status.ready.name(), String.format(
                                    "Deleted volume %s", volume.getNativeId())));
                    if (logMsgBuilder.length() != 0) {
                        logMsgBuilder.append("\n");
                    }
                    logMsgBuilder.append(String.format("Successfully deleted volume %s",
                            volume.getId()));
                }

            } else if (_status == JobStatus.FAILED) {
                super.updateStatus(jobContext);
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
            setErrorStatus("Encountered an internal error during delete volume job status processing: "
                    + e.getMessage());
            super.updateStatus(jobContext);
            _log.error(
                    "Caught exception while handling updateStatus for delete volume job.",
                    e);
        }
    }
}