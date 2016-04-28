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
 * ViPR Job created when an underlying CIM job is created relink
 * a target device to a new array snapshot.
 */
@SuppressWarnings("serial")
public class SmisBlockSnapshotSessionRelinkTargetJob extends SmisJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockSnapshotSessionRelinkTargetJob";

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockSnapshotSessionRelinkTargetJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockSnapshotSessionRelinkTargetJob(CIMObjectPath cimJob, URI systemURI, TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        JobStatus jobStatus = getJobStatus();
        try {
            if (jobStatus == JobStatus.IN_PROGRESS) {
                return;
            }
            if (jobStatus == JobStatus.SUCCESS) {
                s_logger.info("Post-processing successful for re-link snapshot session target for task ", getTaskCompleter().getOpId());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to re-link snapshot session target for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in re-link snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in re-link snapshot session target job status processing", e);
        } finally {
            super.updateStatus(jobContext);
        }
    }
}