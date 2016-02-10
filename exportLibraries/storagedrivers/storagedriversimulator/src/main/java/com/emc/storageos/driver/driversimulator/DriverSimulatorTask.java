package com.emc.storageos.driver.driversimulator;

import com.emc.storageos.storagedriver.DriverTask;

public class DriverSimulatorTask extends DriverTask {

    public DriverSimulatorTask(String taskId) {
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
