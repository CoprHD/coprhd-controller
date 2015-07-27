/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.model.suite;

import java.util.Calendar;
import java.util.List;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindow;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowLengthType;
import com.emc.storageos.db.client.model.uimodels.ExecutionWindowType;
import com.emc.sa.model.dao.ModelClient;

public class ExecutionWindowTest extends BaseModelTest<ExecutionWindow> {
    
    private static final Logger _logger = Logger.getLogger(ExecutionWindowTest.class);
    
    public ExecutionWindowTest() {
        super(ExecutionWindow.class);
    }

    @Test
    public void testPersistObject() throws Exception {
        
        _logger.info("Starting persist ExecutionWindow test");

        ExecutionWindow model = new ExecutionWindow();
        model.setLabel("foo");
        Integer hour = new Integer(11);
        Integer minute = new Integer(53);
        model.setHourOfDayInUTC(hour);
        model.setMinuteOfHourInUTC(minute);
        Integer length = new Integer(4);
        model.setExecutionWindowLength(length);
        model.setExecutionWindowLengthType(ExecutionWindowLengthType.HOURS.name());
        model.setExecutionWindowType(ExecutionWindowType.DAILY.name());
        model.setDayOfWeek(Calendar.TUESDAY);
        model.setDayOfMonth(12);
        model.setTenant(DEFAULT_TENANT);
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(hour, model.getHourOfDayInUTC());
        Assert.assertEquals(minute, model.getMinuteOfHourInUTC());
        Assert.assertEquals(length, model.getExecutionWindowLength());
        Assert.assertEquals(ExecutionWindowLengthType.HOURS.name(), model.getExecutionWindowLengthType());
        Assert.assertEquals(ExecutionWindowType.DAILY.name(), model.getExecutionWindowType());
        Assert.assertEquals(new Integer(Calendar.TUESDAY), model.getDayOfWeek());
        Assert.assertEquals(new Integer(12), model.getDayOfMonth());
        Assert.assertEquals(DEFAULT_TENANT, model.getTenant());
        
    }
    
    @Test
    public void testMultiTenant() throws Exception {
        _logger.info("Starting multi tenant ExecutionWindow test");

        ModelClient modelClient = getModelClient();
        
        ExecutionWindow ew1 = create("t1", "foo1");
        modelClient.save(ew1);
        
        ExecutionWindow ew2 = create("t1", "bar2");
        modelClient.save(ew2);        

        ExecutionWindow ew3 = create("t2", "foo3");
        modelClient.save(ew3);        

        ExecutionWindow ew4 = create("t2", "bar4");
        modelClient.save(ew4);        
        
        ExecutionWindow ew5 = create("t2", "foo5");
        modelClient.save(ew5);        
        
        ExecutionWindow ew6 = create("t3", "bar6");
        modelClient.save(ew6);        
        
        List<ExecutionWindow> executionWindows = modelClient.executionWindows().findAll("t1");
        Assert.assertNotNull(executionWindows);
        Assert.assertEquals(2, executionWindows.size());
        
        executionWindows = modelClient.executionWindows().findAll("t2");
        Assert.assertNotNull(executionWindows);
        Assert.assertEquals(3, executionWindows.size());        

        executionWindows = modelClient.executionWindows().findAll("t3");
        Assert.assertNotNull(executionWindows);
        Assert.assertEquals(1, executionWindows.size());        
        
    }
    
    private static ExecutionWindow create(String tenant, String label) {
        ExecutionWindow model = new ExecutionWindow();
        model.setLabel(label);
        model.setHourOfDayInUTC(11);
        model.setMinuteOfHourInUTC(53);
        Integer length = new Integer(4);
        model.setExecutionWindowLength(length);
        model.setExecutionWindowLengthType(ExecutionWindowLengthType.HOURS.name());
        model.setExecutionWindowType(ExecutionWindowType.DAILY.name());
        model.setDayOfWeek(Calendar.TUESDAY);
        model.setDayOfMonth(12);
        model.setTenant(tenant);
        return model;
    }
}
