/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.block.taskcompleter;

import java.io.Serializable;

import com.emc.storageos.volumecontroller.Job;
import com.emc.storageos.volumecontroller.TaskCompleter;

/**
 * Simple wrapper for TaskCompleter to use in sequential meta volume operations.
 * Adds member to keep status of the last step.
 */
@SuppressWarnings("serial")
public class MetaVolumeTaskCompleter implements Serializable {

    TaskCompleter _volumeTaskCompleter;
    Job.JobStatus _lastStepStatus = Job.JobStatus.SUCCESS;

    public  MetaVolumeTaskCompleter(TaskCompleter volumeTaskCompleter) {
         _volumeTaskCompleter = volumeTaskCompleter;
    }

    public TaskCompleter getVolumeTaskCompleter() {
        return _volumeTaskCompleter;
    }

    public Job.JobStatus getLastStepStatus() {
        return _lastStepStatus;
    }

    public void setLastStepStatus(Job.JobStatus _lastStepStatus) {
        this._lastStepStatus = _lastStepStatus;
    }
}
