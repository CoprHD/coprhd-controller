package com.emc.sa.model.suite;

import java.net.URI;
import java.util.Date;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import com.emc.sa.model.BaseModelTest;
import com.emc.storageos.db.client.model.uimodels.ExecutionLog;
import com.emc.storageos.db.client.model.uimodels.ExecutionState;
import com.emc.storageos.db.client.model.uimodels.ExecutionStatus;
import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.storageos.db.client.URIUtil;

public class ExecutionStateTest extends BaseModelTest<ExecutionState> {

    private static final Logger _logger = Logger.getLogger(ExecutionStateTest.class);
    
    public ExecutionStateTest() {
        super(ExecutionState.class);
    }
    
    @Test
    public void testPersistObject() throws Exception {
        _logger.info("Starting persist ExecutionState test");

        ExecutionState model = new ExecutionState();
        model.setLabel("foo");
        model.getAffectedResources().add("resource one");
        model.getAffectedResources().add("resource two");
        Date s = new Date();
        Date e = new Date();
        model.setStartDate(s);
        model.setEndDate(e);
        model.setCurrentTask("my current task");
        model.setExecutionStatus(ExecutionStatus.COMPLETED.name());
        URI logIdOne = URIUtil.createId(ExecutionLog.class);
        URI logIdTwo = URIUtil.createId(ExecutionLog.class);
        model.getLogIds().add(logIdOne.toString());
        model.getLogIds().add(logIdTwo.toString());
        URI taskLogIdOne = URIUtil.createId(ExecutionTaskLog.class);
        URI taskLogIdTwo = URIUtil.createId(ExecutionTaskLog.class);
        model.getTaskLogIds().add(taskLogIdOne.toString());
        model.getTaskLogIds().add(taskLogIdTwo.toString());
        
        save(model);
        model = findById(model.getId());
        
        Assert.assertNotNull(model);
        Assert.assertEquals("foo", model.getLabel());
        Assert.assertEquals(2, model.getAffectedResources().size());
        Assert.assertTrue(model.getAffectedResources().contains("resource one"));
        Assert.assertTrue(model.getAffectedResources().contains("resource two"));
        Assert.assertEquals(s, model.getStartDate());
        Assert.assertEquals(e, model.getEndDate());
        Assert.assertEquals("my current task", model.getCurrentTask());
        Assert.assertEquals(2, model.getLogIds().size());
        Assert.assertTrue(model.getLogIds().contains(logIdOne.toString()));
        Assert.assertTrue(model.getLogIds().contains(logIdTwo.toString()));
        Assert.assertEquals(2,  model.getTaskLogIds().size());
        Assert.assertTrue(model.getTaskLogIds().contains(taskLogIdOne.toString()));
        Assert.assertTrue(model.getTaskLogIds().contains(taskLogIdTwo.toString()));
        
    }
    
}
