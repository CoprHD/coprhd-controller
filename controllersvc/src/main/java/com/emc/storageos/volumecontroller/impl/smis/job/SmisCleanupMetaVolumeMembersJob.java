/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import javax.wbem.CloseableIterator;
import java.net.URI;
import java.util.ArrayList;

public class SmisCleanupMetaVolumeMembersJob extends SmisJob {

    private static final Logger _log = LoggerFactory.getLogger(SmisCleanupMetaVolumeMembersJob.class);
    CleanupMetaVolumeMembersCompleter cleanupCompleter;
    URI storageSystemURI;
    URI volumeURI;

    public SmisCleanupMetaVolumeMembersJob(CIMObjectPath cimJob, URI storageSystemURI, URI volumeURI,
            CleanupMetaVolumeMembersCompleter cleanupCompleter) {

        super(cimJob, storageSystemURI, cleanupCompleter, "CleanupMetaVolumeMembers");
        this.cleanupCompleter = cleanupCompleter;
        this.storageSystemURI = storageSystemURI;
        this.volumeURI = volumeURI;
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        CloseableIterator<CIMObjectPath> iterator = null;
        DbClient dbClient = jobContext.getDbClient();
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == Job.JobStatus.IN_PROGRESS) {
                return;
            }

            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating status of job %s to %s", this.getJobName(), jobStatus.name()));
            _log.info(logMsgBuilder.toString());

            if (jobStatus == Job.JobStatus.SUCCESS) {
                // clean meta volume members in source step data
                String sourceStepId = cleanupCompleter.getSourceStepId();
                WorkflowService.getInstance().storeStepData(sourceStepId, new ArrayList<String>());
                // Reset list of meta member volumes in the meta head
                Volume metaHead = dbClient.queryObject(Volume.class, volumeURI);
                if (metaHead.getMetaVolumeMembers() != null) {
                    metaHead.getMetaVolumeMembers().clear();
                    dbClient.persistObject(metaHead);
                }
                cleanupCompleter.complete(Workflow.StepState.SUCCESS, null);
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembersJob");
                cleanupCompleter.complete(Workflow.StepState.ERROR, serviceError);
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for CleanupMetaVolumeMembersJob", e);
            setFatalErrorStatus(e.getMessage());
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            cleanupCompleter.complete(Workflow.StepState.ERROR, serviceError);

        }
    }
}
