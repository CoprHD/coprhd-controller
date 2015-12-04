package com.emc.storageos.driver.scaleio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.storagedriver.DriverTask;

class DriverTaskImpl extends DriverTask {
    private static final Logger log = LoggerFactory.getLogger(DriverTaskImpl.class);

    public DriverTaskImpl(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {
        task.setMessage("Task " + task.getTaskId() + " is aborted!");
        task.setStatus(TaskStatus.ABORTED);
        return task;
    }
}
