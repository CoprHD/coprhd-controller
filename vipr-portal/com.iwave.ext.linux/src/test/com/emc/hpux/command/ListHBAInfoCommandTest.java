/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import static org.easymock.EasyMock.createMockBuilder;

import java.util.List;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.model.HBAInfo;

public class ListHBAInfoCommandTest {

    static String output = "host: /dev/fclp0" + "\n" +
            "             N_Port Node World Wide Name = 0x2000000000000001" + "\n" +
            "             N_Port Port World Wide Name = 0x1000000000000001" + "\n" +
            "host: /dev/fclp1" + "\n" +
            "             N_Port Node World Wide Name = 0x2000000000000002" + "\n" +
            "             N_Port Port World Wide Name = 0x1000000000000002";

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
        Assert.assertEquals("2000000000000001", results.get(0).getWwnn());
        Assert.assertEquals("1000000000000001", results.get(0).getWwpn());

        Assert.assertEquals(1, results.get(1).getHostId());
        Assert.assertEquals("2000000000000002", results.get(1).getWwnn());
        Assert.assertEquals("1000000000000002", results.get(1).getWwpn());
    }

}
