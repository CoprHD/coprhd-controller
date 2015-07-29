/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;

public class ExecutionTaskLogTest extends BaseModelTest<ExecutionTaskLog> {

    private static final Logger _logger = Logger.getLogger(ExecutionTaskLogTest.class);

    public ExecutionTaskLogTest() {
        super(ExecutionTaskLog.class);
    }

    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist ExecutionTaskLog test");

        ExecutionTaskLog model = new ExecutionTaskLog();
        model.setLabel("foo");
        model.setDetail("my detail");
        Long elapsed = new Long(42);
        model.setElapsed(elapsed);
        model.setStackTrace("my stack trace");

        save(model);
        model = findById(model.getId());

        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals("my detail", model.getDetail());
        Assert.assertEquals(elapsed, model.getElapsed());
        Assert.assertEquals("my stack trace", model.getStackTrace());

    }
}
