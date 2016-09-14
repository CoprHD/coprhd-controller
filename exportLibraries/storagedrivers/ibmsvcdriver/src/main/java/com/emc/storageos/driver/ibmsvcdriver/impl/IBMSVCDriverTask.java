/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.driver.ibmsvcdriver.impl;

import com.emc.storageos.storagedriver.DriverTask;

public class IBMSVCDriverTask extends DriverTask {

    public IBMSVCDriverTask(String taskId) {
        super(taskId);
    }

    @Override
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
