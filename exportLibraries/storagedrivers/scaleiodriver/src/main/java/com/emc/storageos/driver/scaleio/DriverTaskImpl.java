/*
 * Copyright 2016 Oregon State University
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

package com.emc.storageos.driver.scaleio;

import com.emc.storageos.storagedriver.DriverTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

class DriverTaskImpl extends DriverTask {
    private static final Logger log = LoggerFactory.getLogger(DriverTaskImpl.class);

    public DriverTaskImpl(String taskId) {
        super(taskId);
        this.setStartTime(Calendar.getInstance());
    }

    /**
     * Abort the driver
     * 
     * @param task
     * @return task
     */
    @Override
    public DriverTask abort(DriverTask task) {
        task.setMessage("Task " + task.getTaskId() + " is aborted!");
        task.setStatus(TaskStatus.ABORTED);
        return task;
    }
}
