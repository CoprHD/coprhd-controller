/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

import static org.easymock.EasyMock.createMockBuilder;

import java.util.List;

import junit.framework.Assert;

import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.model.HBAInfo;

public class ListHBAInfoCommandTest {

    static String output = "host: fcs0\n" +
            "Network Address.............C066666666666666\n" +
            "Device Specific.(Z8)........C077777777777777\n" +
            "host: fcs1\n" +
            "Network Address.............C088888888888888\n" +
            "Device Specific.(Z8)........C099999999999999";

    static ListHBAInfoCommand hbaCommand = null;

    @BeforeClass
    public synchronized static void setup() {
        CommandOutput commandOutput = new CommandOutput(output, null, 0);
        hbaCommand = createMockBuilder(ListHBAInfoCommand.class).withConstructor().addMockedMethod("getOutput").createMock();
        EasyMock.expect(hbaCommand.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(hbaCommand);
    }

    @Test
    public void testCommand() {
        hbaCommand.parseOutput();
        List<HBAInfo> results = hbaCommand.getResults();
        Assert.assertEquals(2, results.size());

        Assert.assertEquals(0, results.get(0).getHostId());
        Assert.assertEquals("c066666666666666", results.get(0).getWwpn());
        Assert.assertEquals("c077777777777777", results.get(0).getWwnn());

        Assert.assertEquals(1, results.get(1).getHostId());
        Assert.assertEquals("c088888888888888", results.get(1).getWwpn());
        Assert.assertEquals("c099999999999999", results.get(1).getWwnn());
    }

}
