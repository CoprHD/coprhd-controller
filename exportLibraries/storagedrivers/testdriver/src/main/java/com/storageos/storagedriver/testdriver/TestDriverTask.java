package com.storageos.storagedriver.testdriver;

import com.emc.storageos.storagedriver.DriverTask;

public class TestDriverTask extends DriverTask {

    public TestDriverTask(String taskId) {
        super(taskId);
    }

    public DriverTask abort(DriverTask task) {
        DriverTask abortTaskTask = new DriverTask("Abort task: 1234") {
            public DriverTask abort(DriverTask task) {
                return null;
            }
        };
        abortTaskTask.setStatus(TaskStatus.FAILED);
        abortTaskTask.setMessage("Operation is not supported.");
        return abortTaskTask;
    }
}
