/*
 * Copyright 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.hp3par.impl;

import com.emc.storageos.storagedriver.DriverTask;

public class HP3PARDriverTask extends DriverTask {

    public HP3PARDriverTask(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {
        DriverTask abortTaskTask = new DriverTask("Abort HP3PAR Driver task") {
            public DriverTask abort(DriverTask task) {
                return null;
            }
        };
        abortTaskTask.setStatus(TaskStatus.FAILED);
        abortTaskTask.setMessage("Operation is not supported.");
        return abortTaskTask;
    }
}
