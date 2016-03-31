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

import com.emc.hpux.model.MountPoint;
import com.iwave.ext.command.CommandOutput;

public class ListMountpointsCommandTest {

    private static final String OUTPUT =
            "/ on /dev/vg00/lvol3 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000003 on Wed Nov 18 13:00:16 2015"
                    + "\n"
                    +
                    "/stand on /dev/vg00/lvol1 ioerror=mwdisable,nolargefiles,log,nodatainlog,tranflush,dev=40000001 on Wed Nov 18 13:00:22 2015"
                    + "\n" +
                    "/var on /dev/vg00/lvol8 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000008 on Wed Nov 18 13:00:34 2015"
                    + "\n" +
                    "/usr on /dev/vg00/lvol7 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000007 on Wed Nov 18 13:00:34 2015"
                    + "\n" +
                    "/tmp on /dev/vg00/lvol4 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000004 on Wed Nov 18 13:00:34 2015"
                    + "\n" +
                    "/opt on /dev/vg00/lvol6 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000006 on Wed Nov 18 13:00:35 2015"
                    + "\n" +
                    "/home on /dev/vg00/lvol5 ioerror=mwdisable,largefiles,delaylog,nodatainlog,dev=40000005 on Wed Nov 18 13:00:35 2015"
                    + "\n" +
                    "/net on -hosts ignore,indirect,nosuid,soft,nobrowse,dev=4000002 on Wed Nov 18 13:00:57 2015";

    private static ListMountPointsCommand command = null;

    @BeforeClass
    public synchronized static void setup() {
        CommandOutput commandOutput = new CommandOutput(OUTPUT, null, 0);
        command = createMockBuilder(ListMountPointsCommand.class).withConstructor().addMockedMethod("getOutput").createMock();
        EasyMock.expect(command.getOutput()).andReturn(commandOutput).anyTimes();
        EasyMock.replay(command);
    }

    @Test
    public void testCommand() {
        command.parseOutput();
        List<MountPoint> results = command.getResults();

        System.out.println(results);
    }

}
