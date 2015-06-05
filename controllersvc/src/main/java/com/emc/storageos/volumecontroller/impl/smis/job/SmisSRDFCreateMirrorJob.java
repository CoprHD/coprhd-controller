/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.smis.SmisException;
import com.emc.storageos.workflow.WorkflowStepCompleter;

public class SmisSRDFCreateMirrorJob extends SmisJob {
    private static final Logger log = LoggerFactory.getLogger(SmisSRDFCreateMirrorJob.class);
    private static final String JOB_NAME = "Create SRDF mirror";
    
    public SmisSRDFCreateMirrorJob(final CIMObjectPath cimJob, final URI storageSystem,
            final TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, JOB_NAME);
    }
    
    @Override
    public void updateStatus(final JobContext jobContext) throws Exception {
        log.info("START SmisSRDFCreateMirrorJob#updateStatus");
        JobStatus jobStatus = getJobStatus();
        try {
            
            switch (jobStatus) {
            case IN_PROGRESS:
                handleInProgress();
                break;
            case SUCCESS:
                handleSuccess(jobContext);
                break;
            default:
                log.error("Unable to handle job state: {}", jobStatus.name());
            }
            
        } catch (Exception e) {
            TaskCompleter completer = getTaskCompleter();
            String msg = String.format("Failed to update status for task %s on volume %s",
                    completer.getOpId(), completer.getId());
            log.error(msg, e);
            setPostProcessingFailedStatus(msg+": " +e.getMessage());
            ServiceError error = SmisException.errors.jobFailed(e.getMessage());
            completer.error(jobContext.getDbClient(), error);
        } finally {
            
        }
    }
    
    private void handleInProgress() {
        log.info("START handle in progress");
        TaskCompleter completer = getTaskCompleter();
        WorkflowStepCompleter.stepExecuting(completer.getOpId());
    }
    
    private void handleSuccess(final JobContext jobContext) {
        log.info("Successfully created SRDF mirror from {}", getTaskCompleter().getIds());
        getTaskCompleter().ready(jobContext.getDbClient());
    }
}
