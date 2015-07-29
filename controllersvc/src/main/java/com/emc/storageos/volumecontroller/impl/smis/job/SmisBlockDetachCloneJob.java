/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/*
 * Copyright (c) $today_year. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.smis.job;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.block.taskcompleter.VolumeDetachCloneCompleter;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.cim.CIMObjectPath;
import java.net.URI;

public class SmisBlockDetachCloneJob extends SmisJob {
    private static final Logger _log = LoggerFactory.getLogger(SmisBlockDetachCloneJob.class);

    public SmisBlockDetachCloneJob(CIMObjectPath cimJob, URI storageSystem, TaskCompleter taskCompleter) {
        super(cimJob, storageSystem, taskCompleter, "Detach clone");
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        _log.info("START updateStatus for clone detach");
        JobStatus jobStatus = getJobStatus();
        super.updateStatus(jobContext);

        if (jobStatus == JobStatus.IN_PROGRESS) {
            return;
        }

        DbClient dbClient = jobContext.getDbClient();
        VolumeDetachCloneCompleter taskCompleter = (VolumeDetachCloneCompleter) getTaskCompleter();
        Volume cloneVolume = dbClient.queryObject(Volume.class, taskCompleter.getId());
        Operation.Status op = Operation.Status.pending;
        String message = "Detaching volume clone";

        if (jobStatus == JobStatus.SUCCESS) {
            op = Operation.Status.ready;
            message = "Successfully detached volume clone";
        } else if (jobStatus == JobStatus.ERROR || jobStatus == JobStatus.FAILED || jobStatus == JobStatus.FATAL_ERROR) {
            op = Operation.Status.error;
            message = "Failed to detached volume clone";
        }
        // set to terminal status to stop polling job
        if (jobStatus == JobStatus.ERROR) {
            setFatalErrorStatus(message);
        }
        dbClient.updateTaskOpStatus(Volume.class, cloneVolume.getId(), taskCompleter.getOpId(),
                new Operation(op.name(), message));
        WorkflowStepCompleter.updateState(taskCompleter.getOpId(), op, message);
    }
}
