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
 * ViPR Job created when an underlying CIM job is created to unlink
 * a target volume from an array snapshot.
 */
@SuppressWarnings("serial")
public class SmisBlockUnlinkSnapshotSessionTargetJob extends SmisJob {

    // The unique job name.
    private static final String JOB_NAME = "SmisBlockUnlinkSnapshotSessionTargetJob";

    // Whether or not the target was deleted when it was unlinked.
    @SuppressWarnings("unused")
    private final Boolean _deleteTarget;

    // Reference to a logger.
    private static final Logger s_logger = LoggerFactory.getLogger(SmisBlockUnlinkSnapshotSessionTargetJob.class);

    /**
     * Constructor.
     * 
     * @param cimJob The CIM object path of the underlying CIM Job.
     * @param systemURI The URI of the storage system.
     * @param deleteTarget Whether or not the target was deleted when it was unlinked.
     * @param taskCompleter A reference to the task completer.
     */
    public SmisBlockUnlinkSnapshotSessionTargetJob(CIMObjectPath cimJob, URI systemURI, Boolean deleteTarget,
            TaskCompleter taskCompleter) {
        super(cimJob, systemURI, taskCompleter, JOB_NAME);
        _deleteTarget = deleteTarget;
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
                s_logger.info("Post-processing successful for unlink snapshot session target for task ", getTaskCompleter().getOpId());
            } else if (jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
                s_logger.info("Failed to unlink snapshot session target for task ", getTaskCompleter().getOpId());
            }
        } catch (Exception e) {
            setPostProcessingErrorStatus("Encountered an internal error in unlink snapshot session target job status processing: "
                    + e.getMessage());
            s_logger.error("Encountered an internal error in unlink snapshot session target job status processing", e);
        } finally {
            super.updateStatus(jobContext);
        }
    }
}
