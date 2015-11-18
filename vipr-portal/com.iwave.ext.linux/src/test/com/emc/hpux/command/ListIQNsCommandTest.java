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
            "Initiator Name            : iqn.1986-03.com.hp:LGLAL017.b0b29429-b3d5-11e3-bc62-145e2199962a" + "\n" +
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
        Assert.assertEquals("iqn.1986-03.com.hp:LGLAL017.b0b29429-b3d5-11e3-bc62-145e2199962a", results.iterator().next());
    }

}
