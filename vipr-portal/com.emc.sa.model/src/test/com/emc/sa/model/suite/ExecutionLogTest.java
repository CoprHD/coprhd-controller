/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.util.Date;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog.LogLevel;
import com.emc.storageos.db.client.model.uimodels.ExecutionPhase;
import com.emc.storageos.db.client.URIUtil;

public class ExecutionLogTest extends BaseModelTest<ExecutionLog> {

    private static final Logger _logger = Logger.getLogger(ExecutionLogTest.class);

    public ExecutionLogTest() {
        super(ExecutionLog.class);
    }

    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist ExecutionLog test");

        ExecutionLog model = new ExecutionLog();
        model.setId(URIUtil.createId(ExecutionLog.class));
        model.setLabel("foo");
        Date d = new Date();
        model.setDate(d);
        model.setLevel(LogLevel.WARN.name());
        model.setMessage("my message");
        model.setStackTrace("my stack trace");
        model.setPhase(ExecutionPhase.ROLLBACK.name());

        save(model);
        model = findById(model.getId());

        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(d, model.getDate());
        Assert.assertEquals(LogLevel.WARN.name(), model.getLevel());
        Assert.assertEquals("my message", model.getMessage());
        Assert.assertEquals("my stack trace", model.getStackTrace());
        Assert.assertEquals(ExecutionPhase.ROLLBACK.name(), model.getPhase());
    }

    protected static ExecutionLog create(String label, String message, LogLevel level) {
        ExecutionLog model = new ExecutionLog();
        model.setLabel(label);
        model.setDate(new Date());
        model.setLevel(level.name());
        model.setMessage(message);
        model.setPhase(ExecutionPhase.EXECUTE.name());
        return model;
    }
}
