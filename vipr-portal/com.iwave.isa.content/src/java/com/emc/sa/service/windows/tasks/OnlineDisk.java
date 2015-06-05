/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class OnlineDisk extends DiskPartScript<Void> {
    private int diskNumber;
    private boolean currentReadOnlyState;

    public OnlineDisk(int diskNumber, boolean currentReadOnlyState) {
        this.diskNumber = diskNumber;
        this.currentReadOnlyState = currentReadOnlyState;
        setDiskPartCommands(WindowsUtils.getOnlineDiskCommands(diskNumber, currentReadOnlyState));
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().onlineDisk(diskNumber, currentReadOnlyState);
        logDebug(output);
    }
}
