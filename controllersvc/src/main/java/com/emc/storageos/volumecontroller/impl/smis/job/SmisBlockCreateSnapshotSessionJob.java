/*
 * Copyright (c) 2012 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import java.net.URI;

import javax.cim.CIMObjectPath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * 
 */
@SuppressWarnings("serial")
public class SmisBlockCreateSnapshotSessionJob extends SmisJob {

    private static final String JOB_NAME = "SmisBlockCreateSnapshotSessionJob";

    //
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockCreateSnapshotSessionJob.class);

    /**
     * 
     * @param cimJob
     * @param systemURI
     * @param taskCompleter
     */
    public SmisBlockCreateSnapshotSessionJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                s_logger.info("Post-processing successful snapshot session creation for task ", getTaskCompleter().getOpId());
                // TBD- Update Settings instance. I am not sure this actually moves to the session.
                // I think it still may stay in BlockSnapshot and get set for each target linked
                // to the session. I think I will have to undeprecate this and remove from session.
                // Otherwise there is really nothing in the session needing updating.
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to create snapshot session for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in create snapshot session job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in create snapshot session job status processing", e);
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
