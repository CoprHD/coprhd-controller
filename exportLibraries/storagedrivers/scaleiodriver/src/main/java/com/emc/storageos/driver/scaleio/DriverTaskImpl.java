package com.emc.storageos.driver.scaleio;

import com.emc.storageos.storagedriver.DriverTask;


public class DriverTaskImpl extends DriverTask {

    public DriverTaskImpl(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {

        task.setMessage("Task "+task.getTaskId()+" is aborted!");
        task.setStatus(TaskStatus.ABORTED);
        return task;
    }
}
