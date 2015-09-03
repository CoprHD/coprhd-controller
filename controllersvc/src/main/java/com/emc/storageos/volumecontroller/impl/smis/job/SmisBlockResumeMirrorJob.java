/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.BlockMirror;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.BlockMirrorResumeCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.client.WBEMClient;
import java.net.URI;

import static java.lang.String.format;

public class SmisBlockResumeMirrorJob extends SmisBlockMirrorJob {
    private static final Logger log = LoggerFactory.getLogger(SmisBlockResumeMirrorJob.class);

    public SmisBlockResumeMirrorJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "ResumeMirror");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        log.info("START updateStatus for resuming {}", getTaskCompleter().getId());
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }

            BlockMirrorResumeCompleter taskCompleter = (BlockMirrorResumeCompleter) getTaskCompleter();
            if (jobStatus == JobStatus.SUCCESS) {
                log.info("Mirror resume success");
                BlockMirror mirror = dbClient.queryObject(BlockMirror.class, taskCompleter.getMirrorURI());

                log.info("Updating sync details for mirror {}", mirror.getId());
                WBEMClient client = getWBEMClient(dbClient, jobContext.getCimConnectionFactory());
                updateSynchronizationAspects(client, mirror);
                dbClient.persistObject(mirror);
                getTaskCompleter().ready(dbClient);
            } else if (jobStatus == JobStatus.ERROR) {
                log.info("Mirror resume failed");
            }
        } catch (Exception e) {
            String errorMsg = "Failed job to resume mirror: " + e.getMessage();
            log.error(errorMsg, e);
            setPostProcessingErrorStatus(errorMsg);
        } finally {
            super.updateStatus(jobContext);
            log.info("FINISH updateStatus for resuming {}", getTaskCompleter().getId());
        }
    }
}
