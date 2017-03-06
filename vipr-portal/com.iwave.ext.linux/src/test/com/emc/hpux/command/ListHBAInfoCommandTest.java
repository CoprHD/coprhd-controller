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

    private static String output = "host: /dev/fclp0" + "\n" +
            "             N_Port Node World Wide Name = 0x2000000000000001" + "\n" +
            "             N_Port Port World Wide Name = 0x1000000000000001" + "\n" +
            "host: /dev/fclp1" + "\n" +
            "             N_Port Node World Wide Name = 0x2000000000000002" + "\n" +
            "             N_Port Port World Wide Name = 0x1000000000000002" + "\n" +
            "host: /dev/td0" + "\n" +
            "             N_Port Node World Wide Name = 0x5000000000000001" + "\n" +
            "             N_Port Port World Wide Name = 0x5000000000000001" + "\n" +
            "host: /dev/td1" + "\n" +
            "             N_Port Node World Wide Name = 0x5000000000000002" + "\n" +
            "             N_Port Port World Wide Name = 0x5000000000000002" + "\n" +
            "host: /dev/nonumber" + "\n" +
            "             N_Port Node World Wide Name = 0x5000000000000003" + "\n" +
            "             N_Port Port World Wide Name = 0x5000000000000003" + "\n" +
            "host: mydevice" + "\n" +
            "             N_Port Node World Wide Name = 0x5000000000000004" + "\n" +
            "             N_Port Port World Wide Name = 0x5000000000000004";

    private static ListHBAInfoCommand hbaCommand = null;

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
        Assert.assertEquals(6, results.size());

        Assert.assertEquals("2000000000000001", results.get(0).getWwnn());
        Assert.assertEquals("1000000000000001", results.get(0).getWwpn());

        Assert.assertEquals("2000000000000002", results.get(1).getWwnn());
        Assert.assertEquals("1000000000000002", results.get(1).getWwpn());

        Assert.assertEquals("5000000000000001", results.get(2).getWwnn());
        Assert.assertEquals("5000000000000001", results.get(2).getWwpn());

        Assert.assertEquals("5000000000000002", results.get(3).getWwnn());
        Assert.assertEquals("5000000000000002", results.get(3).getWwpn());

        Assert.assertEquals("5000000000000003", results.get(4).getWwnn());
        Assert.assertEquals("5000000000000003", results.get(4).getWwpn());

        Assert.assertEquals("5000000000000004", results.get(5).getWwnn());
        Assert.assertEquals("5000000000000004", results.get(5).getWwpn());
    }

}
