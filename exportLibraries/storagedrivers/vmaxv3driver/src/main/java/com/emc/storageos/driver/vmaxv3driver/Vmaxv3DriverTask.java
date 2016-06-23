/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.driver.vmaxv3driver;

import com.emc.storageos.storagedriver.DriverTask;

/**
 * The default DriverTask implementation.
 *
 * Created by gang on 6/21/16.
 */
public class Vmaxv3DriverTask extends DriverTask {

    public Vmaxv3DriverTask(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {
        DriverTask abortTaskTask = new DriverTask("Abort VMAX V3 Driver task") {
            public DriverTask abort(DriverTask task) {
                return null;
            }
        };
        abortTaskTask.setStatus(TaskStatus.FAILED);
        abortTaskTask.setMessage("Operation is not supported.");
        return abortTaskTask;
    }

    @Override
    public String toString() {
        return "Vmaxv3DriverTask{" +
                "taskId='" + this.getTaskId() + '\'' +
                ", status=" + this.getStatus() +
                ", progress=" + this.getProgress() +
                ", message='" + this.getMessage() + '\'' +
                ", startTime=" + this.getStartTime() +
                ", endTime=" + this.getEndTime() +
                '}';
    }
}
