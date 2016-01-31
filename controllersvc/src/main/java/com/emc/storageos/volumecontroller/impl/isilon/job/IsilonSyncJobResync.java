/*
 * Copyright (c) 2015-2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.isilon.job;

import java.net.URI;

import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class IsilonSyncJobResync extends IsilonSyncJobFailover {

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        super.updateStatus(jobContext);
    }

    public IsilonSyncJobResync(String jobId, URI storageSystemUri, TaskCompleter taskCompleter, String jobName) {
        super(jobId, storageSystemUri, taskCompleter, jobName);
    }

}
