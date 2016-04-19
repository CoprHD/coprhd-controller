/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import static org.easymock.EasyMock.createMockBuilder;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.command.CommandOutput;

public class GetNetworkAdapterMacAddressCommandTest {

    private static String output = "0xF0F0F0F0F0F0";

    private static GetNetworkAdapterMacAddressCommand macAddressCommand = null;

    @BeforeClass
    public synchronized static void setup() {
        CommandOutput commandOutput = new CommandOutput(output, null, 0);
        macAddressCommand = createMockBuilder(GetNetworkAdapterMacAddressCommand.class).withConstructor("lan0")
                .addMockedMethod("getOutput")
                .createMock();
        EasyMock.expect(macAddressCommand.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(macAddressCommand);
    }

    @Test
    public void testCommand() {
        macAddressCommand.parseOutput();
        String results = macAddressCommand.getResults();
        Assert.assertEquals("F0:F0:F0:F0:F0:F0", results);
    }

}
