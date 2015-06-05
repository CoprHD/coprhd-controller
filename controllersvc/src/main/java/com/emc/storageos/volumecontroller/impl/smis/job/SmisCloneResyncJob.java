/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;
import java.util.List;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import javax.wbem.client.WBEMClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.Volume.ReplicationState;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.Job.JobStatus;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneCreateCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneResyncCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CloneTaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;

public class SmisCloneResyncJob extends SmisJob {
    
    private static final Logger log = LoggerFactory.getLogger(SmisCloneResyncJob.class);
    
    public SmisCloneResyncJob(CIMObjectPath cimJob,
                              URI storageSystem,
                              TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "ResyncClone");
    }
    
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        log.info("START updateStatus for resync clone");
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            
            if (jobStatus == JobStatus.SUCCESS) {
                CloneResyncCompleter completer = (CloneResyncCompleter) getTaskCompleter();
                List<Volume> cloneVolumes = dbClient.queryObject(Volume.class, completer.getIds());
                log.info("Clone resync success");
                for (Volume clone: cloneVolumes) {
                    clone.setReplicaState(ReplicationState.RESYNCED.name());
                }
                dbClient.persistObject(cloneVolumes);
            }
        } catch (Exception e) {
            String errorMsg = String.format("Encountered an internal error during updating resync clone job status " +
                    "processing: %s", e.getMessage());
            setPostProcessingErrorStatus(errorMsg);
            log.error("Failed to update status for " + getClass().getSimpleName(), e);
        } finally {
            if (iterator != null) {
                iterator.close();
            }
            super.updateStatus(jobContext);
            log.info("FINISH updateStatus for resync clone");
        }
    }


}
