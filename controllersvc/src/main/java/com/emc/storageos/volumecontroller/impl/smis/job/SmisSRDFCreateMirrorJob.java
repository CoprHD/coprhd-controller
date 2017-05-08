/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;

/**
 * This job class is used in the context of a synchronous SMI-S CreateElementReplica request,
 * thus no requirement for handling errors.
 */
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
        // do nothing
    }
}
