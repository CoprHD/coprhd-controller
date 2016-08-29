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

import org.junit.Assert;
import org.junit.Test;

import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;

/**
 * Tests for the Dell driver task.
 */
public class DellSCDriverTaskTest {

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.DellSCDriverTask#setFailed(java.lang.String)}.
     */
    @Test
    public void testSetFailed() {
        String MESSAGE = "Failed Test";
        DellSCDriverTask task = new DellSCDriverTask("test");
        task.setFailed(MESSAGE);
        Assert.assertTrue(MESSAGE.equals(task.getMessage()));
        Assert.assertTrue(task.getStatus() == TaskStatus.FAILED);
    }

    @Test
    public void testTaskCreate() {
        DellSCDriverTask task = new DellSCDriverTask("test");
        Assert.assertTrue(task instanceof DriverTask);
        Assert.assertTrue(task.getTaskId().startsWith("dellsc-test"));
    }

    /**
     * Test method for {@link com.emc.storageos.driver.dellsc.DellSCDriverTask#abort(com.emc.storageos.storagedriver.DriverTask)}.
     */
    @Test
    public void testAbort() {
        DriverTask task = new DellSCDriverTask("test");
        task = task.abort(task);
        // This should fail the abort
        Assert.assertTrue(task.getStatus() == TaskStatus.FAILED);
    }
}
