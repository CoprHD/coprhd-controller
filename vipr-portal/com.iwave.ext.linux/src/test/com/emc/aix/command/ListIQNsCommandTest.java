/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import static org.easymock.EasyMock.createMockBuilder;

import java.util.Set;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.command.CommandOutput;

public class ListIQNsCommandTest {

    static String output = "initiator_name = \"iqn.com.abc.hostid.XYZ\"";
    
    static ListIQNsCommand iqnCommand = null;
    
    @BeforeClass
    public static void setup() {
        CommandOutput commandOutput = new CommandOutput(output, null, 0);
        iqnCommand = createMockBuilder(ListIQNsCommand.class).withConstructor().addMockedMethod("getOutput").createMock();
        EasyMock.expect(iqnCommand.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(iqnCommand);
    }
    
    @Test
    public void testCommand() {
        iqnCommand.parseOutput();
        Set<String> results = iqnCommand.getResults();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("iqn.com.abc.hostid.XYZ", results.iterator().next());
    }

}
