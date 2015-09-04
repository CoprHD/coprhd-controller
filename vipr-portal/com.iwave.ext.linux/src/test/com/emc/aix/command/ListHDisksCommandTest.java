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
import com.iwave.ext.linux.model.PowerPathDevice;

public class ListHDisksCommandTest {

    static String output = "Inquiry utility\n" +
            "Copyright (c) [1997-2013] EMC Corporation. All Rights Reserved.\n" +
            "For help type inq -h.\n" +
            "\n" +
            "....\n" +
            "\n" +
            "----------------------------------------------------------------------------\n" +
            "DEVICE           :VEND    :PROD            :WWN \n" +
            "----------------------------------------------------------------------------\n" +
            "/dev/rhdisk4     :EMC     :SYMMETRIX       :60000000000000000000000000000001\n" +
            "/dev/rhdisk5     :EMC     :SYMMETRIX       :60000000000000000000000000000002\n";

    static ListHDisksCommand hdisksCommand = null;

    @BeforeClass
    public static void setup() {
        CommandOutput commandOutput = new CommandOutput(output, null, 0);
        hdisksCommand = createMockBuilder(ListHDisksCommand.class).addMockedMethod("getOutput").createMock();
        EasyMock.expect(hdisksCommand.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(hdisksCommand);
    }

    @Test
    public void testCommand() {
        hdisksCommand.parseOutput();
        List<PowerPathDevice> results = hdisksCommand.getResults();
        System.out.print(results);
        Assert.assertEquals(2, results.size());
    }

}
