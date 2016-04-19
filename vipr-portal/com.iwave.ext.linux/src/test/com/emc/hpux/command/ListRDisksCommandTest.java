/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import static org.easymock.EasyMock.createMockBuilder;

import java.util.List;

import org.easymock.EasyMock;
import org.junit.BeforeClass;
import org.junit.Test;

import com.emc.hpux.model.RDisk;
import com.iwave.ext.command.CommandOutput;

public class ListRDisksCommandTest {

    private static final String OUTPUT =
            "/dev/pt/pt0:" + "\n" +
                    "/dev/rdisk/disk3:0x5000c5007141fccf" + "\n" +
                    "/dev/rdisk/disk4:0x5000c50071420b87" + "\n" +
                    "/dev/rdisk/disk5:" + "\n" +
                    "/dev/pt/pt3:0x514f0c5000000000" + "\n" +
                    "/dev/pt/pt10:0x60060e801671d500001171d50000ffff" + "\n" +
                    "/dev/rdisk/disk23:0x60060e801671d500000171d500000790" + "\n" +
                    "/dev/rdisk/disk24:0x60060e801671d500000171d50000078f" + "\n" +
                    "/dev/rdisk/disk25:0x60060e801671d500000171d500000791" + "\n" +
                    "/dev/rdisk/disk29:0x514f0c504ae01999" + "\n" +
                    "/dev/rdisk/disk31:0x514f0c594de0181e" + "\n" +
                    "/dev/pt/pt16:0x70090060000195701573000000004701" + "\n" +
                    "/dev/rdisk/disk33:0x60000970000195701573533031304630" + "\n" +
                    "/dev/pt/pt18:0x00000000000000000400000000000000" + "\n" +
                    "/dev/rdisk/disk35:0x6000144000000010f07dc46a07198e5d" + "\n" +
                    "/dev/rdisk/disk39:0x514f0c594de0182b" + "\n" +
                    "/dev/rdisk/disk41:0x60000970000195701573533032433644";

    private static ListRDisksCommand command = null;

    @BeforeClass
    public synchronized static void setup() {
        CommandOutput commandOutput = new CommandOutput(OUTPUT, null, 0);
        command = createMockBuilder(ListRDisksCommand.class).withConstructor().addMockedMethod("getOutput").createMock();
        EasyMock.expect(command.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(command);
    }

    @Test
    public void testCommand() {
        command.parseOutput();
        List<RDisk> results = command.getResults();

        System.out.println(results);
    }

}
