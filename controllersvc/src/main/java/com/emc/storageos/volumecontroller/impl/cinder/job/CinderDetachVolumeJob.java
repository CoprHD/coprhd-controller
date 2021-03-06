/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.cinder.job;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.cinder.CinderConstants;
import com.emc.storageos.cinder.CinderEndPointInfo;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;

public class CinderDetachVolumeJob extends CinderJob {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CinderDetachVolumeJob.class);

    public CinderDetachVolumeJob(String jobId, String jobName,
            URI storageSystem, String componentType, CinderEndPointInfo ep,
            TaskCompleter taskCompleter) {
        super(jobId, "DetachVolume:VolumeName:" + jobName, storageSystem, componentType, ep, taskCompleter);
    }

    @Override
    public void updateStatus(JobContext jobContext) throws Exception {
        // TODO Auto-generated method stub
        super.updateStatus(jobContext);
    }

    @Override
    protected boolean isJobSucceeded(String currentStatus) {
        return (CinderConstants.ComponentStatus.AVAILABLE.getStatus().equalsIgnoreCase(currentStatus));
    }

    @Override
    protected boolean isJobFailed(String currentStatus) {
        return (CinderConstants.ComponentStatus.ERROR.getStatus().equalsIgnoreCase(currentStatus)
        || CinderConstants.ComponentStatus.IN_USE.getStatus().equalsIgnoreCase(currentStatus));
    }
}
