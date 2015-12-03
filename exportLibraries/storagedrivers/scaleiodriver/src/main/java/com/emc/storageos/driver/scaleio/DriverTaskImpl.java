package com.emc.storageos.driver.scaleio;

import com.emc.storageos.storagedriver.DriverTask;

import java.util.Calendar;


public class DriverTaskImpl extends DriverTask {

    public DriverTaskImpl(String taskId) {
        super(taskId);
    }

    @Override
    public DriverTask abort(DriverTask task) {
        //task.setProgress(-1); //set Progress??
        task.setMessage("Task "+task.getTaskId()+" is aborted!");
        task.setEndTime(Calendar.getInstance());
        task.setStatus(TaskStatus.ABORTED);
        return task;
    }
}
