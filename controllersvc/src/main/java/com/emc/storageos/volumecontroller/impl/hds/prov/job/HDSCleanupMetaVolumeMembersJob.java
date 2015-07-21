/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.hds.prov.job;


import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.CleanupMetaVolumeMembersCompleter;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;

public class HDSCleanupMetaVolumeMembersJob extends HDSJob {

    private static final Logger _log = LoggerFactory.getLogger(HDSCleanupMetaVolumeMembersJob.class);
    CleanupMetaVolumeMembersCompleter cleanupCompleter;
    URI storageSystemURI;
    URI volumeURI;

    public HDSCleanupMetaVolumeMembersJob(String asyncMessageId, URI storageSystemURI, URI volumeURI, CleanupMetaVolumeMembersCompleter cleanupCompleter) {

        super(asyncMessageId, storageSystemURI, cleanupCompleter, "CleanupMetaVolumeMembers");
        this.cleanupCompleter = cleanupCompleter;
        this.storageSystemURI = storageSystemURI;
        this.volumeURI = volumeURI;
    }


    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        try {
            if (_status == Job.JobStatus.IN_PROGRESS) {
                return;
            }

            StringBuilder logMsgBuilder =
                    new StringBuilder(String.format("Updating status of job %s to %s", this.getJobName(), _status.name()));
            _log.info(logMsgBuilder.toString());

            if (_status == Job.JobStatus.SUCCESS) {
                // clean meta volume members in source step data
                String sourceStepId = cleanupCompleter.getSourceStepId();
                WorkflowService.getInstance().storeStepData(sourceStepId, new ArrayList<String>());

                cleanupCompleter.complete(Workflow.StepState.SUCCESS, null);
            } else if (_status == JobStatus.FAILED ) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembersJob");
                cleanupCompleter.complete(Workflow.StepState.ERROR, serviceError);
            } else if (_status == JobStatus.ERROR) {
                ServiceError serviceError = DeviceControllerException.errors.jobFailedOp("CleanupMetaVolumeMembersJob");
                cleanupCompleter.complete(Workflow.StepState.ERROR, serviceError);
            }
        } catch (Exception e) {
            _log.error("Caught an exception while trying to updateStatus for CleanupMetaVolumeMembersJob", e);
            ServiceError serviceError = DeviceControllerException.errors.jobFailed(e);
            cleanupCompleter.complete(Workflow.StepState.ERROR, serviceError);
        }
    }
}
