/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;

import com.emc.storageos.storagedriver.DriverTask;

/**
 * Default implementation of DriverTask.
 */
public class DefaultDriverTask extends DriverTask {

    public DefaultDriverTask(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {
        DriverTask abortTaskTask = new DriverTask("AbortTask_"+ task.getTaskId()) {
            public DriverTask abort(DriverTask task) {
                throw new UnsupportedOperationException("Cannot abort abort task");
            }
        };
        abortTaskTask.setStatus(TaskStatus.FAILED);
        abortTaskTask.setMessage("abort operation is not supported for default tasks.");
        return abortTaskTask;
    }
}
