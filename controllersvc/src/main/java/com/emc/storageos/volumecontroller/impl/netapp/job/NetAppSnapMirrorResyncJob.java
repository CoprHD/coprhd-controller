package com.emc.storageos.volumecontroller.impl.netapp.job;

import java.io.Serializable;

import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.JobContext;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.JobPollResult;

public class NetAppSnapMirrorResyncJob extends Job implements Serializable {

    public NetAppSnapMirrorResyncJob() {
        // TODO Auto-generated constructor stub
    }

    @Override
    public JobPollResult poll(JobContext jobContext, long trackingPeriodInMillis) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public TaskCompleter getTaskCompleter() {
        // TODO Auto-generated method stub
        return null;
    }

}
