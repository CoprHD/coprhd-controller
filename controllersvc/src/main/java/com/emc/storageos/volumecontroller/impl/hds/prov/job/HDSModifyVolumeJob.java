/**
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
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.model.LDEV;
import com.emc.storageos.hds.model.LogicalUnit;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;

import org.milyn.payload.JavaResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Iterator;


/**
 * Job for volumeModify operation.
 *
 */
public class HDSModifyVolumeJob extends HDSJob {
    private static final Logger log = LoggerFactory.getLogger(HDSModifyVolumeJob.class);
    
    public static final String VOLUME_MODIFY_JOB = "volumeModify";
    
    public static final String VOLUME_VPOOL_CHANGE_JOB = "volumeVPoolChange";

    public HDSModifyVolumeJob(String jobId, URI storageSystem, TaskCompleter taskCompleter, String jobName) {
        super(jobId, storageSystem, taskCompleter, jobName);
    }

    /**
     * Called to update the job status when the volume expand job completes.
     * 
     * @param jobContext
     *            The job context.
     */
    public void updateStatus(JobContext jobContext) throws Exception {
        LogicalUnit logicalUnit = null;
        try {

            if (_status == JobStatus.IN_PROGRESS) {
                return;
            }
            DbClient dbClient = jobContext.getDbClient();
            StorageSystem storageSystem = dbClient.queryObject(StorageSystem.class,
                    getStorageSystemURI());

            HDSApiClient hdsApiClient = jobContext.getHdsApiFactory().getClient(
                    HDSUtils.getHDSServerManagementServerInfo(storageSystem),
                    storageSystem.getSmisUserName(), storageSystem.getSmisPassword());

            JavaResult javaResult = hdsApiClient
                    .checkAsyncTaskStatus(getHDSJobMessageId());

            String opId = getTaskCompleter().getOpId();
            StringBuilder logMsgBuilder = new StringBuilder(String.format(
                    "Updating status of job %s to %s, task: %s", this.getJobName(),
                    _status.name(), opId));
            if (_status == JobStatus.SUCCESS) {
                logicalUnit = (LogicalUnit) javaResult.getBean("logicalunit");
                if (null != logicalUnit.getLdevList() && !logicalUnit.getLdevList().isEmpty()) {
                    Iterator<LDEV> ldevListItr = logicalUnit.getLdevList().iterator();
                    if(ldevListItr.hasNext()) {
                        LDEV ldev = ldevListItr.next();
                        if (null != ldev && -1 != ldev.getTierLevel()) {
                            logMsgBuilder.append(String.format(
                                    "Task %s is successful to update volume %s tieringPolicy: %s", opId,
                                    logicalUnit.getObjectID(), ldev.getTierLevel()));
                        }
                    }
                }
            } else if (_status == JobStatus.FAILED && VOLUME_MODIFY_JOB.equalsIgnoreCase(getJobName())) {
                URI id = getTaskCompleter().getId();
                logMsgBuilder.append(String.format("Task %s failed to update volume tieringPolicy: %s", opId,
                        id.toString()));
                Volume volume = dbClient.queryObject(Volume.class, id);
                volume.setInactive(true);
                dbClient.persistObject(volume);
            }
            log.info(logMsgBuilder.toString());
        } catch (Exception e) {
            log.error(
                    "Caught an exception while trying to updating tieringPolicy of the volume",
                    e);
            setErrorStatus("Encountered an internal error during tieringPolicy updation of volume: "
                    + e.getMessage());
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
