/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import static org.easymock.EasyMock.createMockBuilder;

import java.util.Set;

import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.iwave.ext.command.CommandOutput;

public class ListIQNsCommandTest {

    static String output =
            "Initiator Name            : iqn.This-Is-A-Test-123456" + "\n" +
                    "Initiator Alias           : " + "\n" +
                    "\n" +
                    "Authentication Method     : " + "\n" +
                    "CHAP Method               : CHAP_UNI" + "\n" +
                    "Initiator CHAP Name       : " + "\n" +
                    "CHAP Secret               : " + "\n" +
                    "NAS Hostname              : " + "\n" +
                    "NAS Secret                : " + "\n" +
                    "Radius Server Hostname    : " + "\n" +
                    "Header Digest             : None,CRC32C (default)" + "\n" +
                    "Data Digest               : None,CRC32C (default)" + "\n" +
                    "SLP Scope list for iSLPD  : ";

    static ListIQNsCommand iqnsCommand = null;

    @BeforeClass
    public synchronized static void setup() {
        CommandOutput commandOutput = new CommandOutput(output, null, 0);
        iqnsCommand = createMockBuilder(ListIQNsCommand.class).withConstructor().addMockedMethod("getOutput").createMock();
        EasyMock.expect(iqnsCommand.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(iqnsCommand);
    }

    @Test
    public void testCommand() {
        iqnsCommand.parseOutput();
        Set<String> results = iqnsCommand.getResults();
        Assert.assertEquals(1, results.size());
        Assert.assertEquals("iqn.This-Is-A-Test-123456", results.iterator().next());
    }

}
