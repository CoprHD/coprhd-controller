/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class OfflineDisk extends DiskPartScript<Void> {
    private int diskNumber;

    public OfflineDisk(int diskNumber) {
        this.diskNumber = diskNumber;
        setDiskPartCommands(WindowsUtils.getOfflineDiskCommands(diskNumber));
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().offlineDisk(diskNumber);
        logDebug(output);
    }
}
