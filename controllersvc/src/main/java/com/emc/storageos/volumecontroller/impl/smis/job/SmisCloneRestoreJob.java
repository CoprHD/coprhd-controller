/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneRestoreCompleter;

public class SmisCloneRestoreJob extends SmisJob {

    private static final Logger log = LoggerFactory.getLogger(SmisCloneRestoreJob.class);

    public SmisCloneRestoreJob(CIMObjectPath cimJob,
            URI storageSystem,
            TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "RestoreVolumeFromClone");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        log.info("START updateStatus for restore clone");
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            CloneRestoreCompleter completer = (CloneRestoreCompleter) getTaskCompleter();
            List<Volume> cloneVolumes = dbClient.queryObject(Volume.class, completer.getIds());
            StorageSystem storage = dbClient.queryObject(StorageSystem.class, getStorageSystemURI());
            if (jobStatus == JobStatus.SUCCESS) {
                log.info("Clone restore success");
                for (Volume clone : cloneVolumes) {
                    clone.setReplicaState(ReplicationState.RESTORED.name());
                }
                dbClient.persistObject(cloneVolumes);
            }
        } catch (Exception e) {
            String errorMsg = String.format("Encountered an internal error during updating restore clone job status " +
                    "processing: %s", e.getMessage());
            setPostProcessingErrorStatus(errorMsg);
            log.error("Failed to update status for " + getClass().getSimpleName(), e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            super.updateStatus(jobContext);
            log.info("FINISH updateStatus for restore clone");
        }
    }

}
