/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc;

import java.util.UUID;

import com.emc.storageos.storagedriver.DriverTask;

/**
 * Dell SC driver task information.
 */
public class DellSCDriverTask extends DriverTask {

    /**
     * Instantiate new task.
     * 
     * @param taskType The type of task.
     */
    public DellSCDriverTask(String taskType) {
        super(String.format("dellsc-%s-%s", taskType, UUID.randomUUID()));
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.emc.storageos.storagedriver.DriverTask#abort(com.emc.storageos.storagedriver.DriverTask)
     */
    @Override
    public DriverTask abort(DriverTask task) {
        DriverTask abortTask = new DriverTask("Abort driver task") {
            @Override
            public DriverTask abort(DriverTask task) {
                return null;
            }
        };
        abortTask.setStatus(TaskStatus.FAILED);
        abortTask.setMessage("Operation is not supported.");
        return abortTask;
    }

    /**
     * Mark a task as failed.
     * 
     * @param failureMessage The failure reason.
     */
    public void setFailed(String failureMessage) {
        setMessage(failureMessage);
        setStatus(TaskStatus.FAILED);
    }
}
