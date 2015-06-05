/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class UnmountVolume extends DiskPartScript<Void> {
    private int volumeNumber;
    private String mountpoint;

    public UnmountVolume(int volumeNumber, String mountpoint) {
        this.volumeNumber = volumeNumber;
        this.mountpoint = mountpoint;
        setDiskPartCommands(WindowsUtils.getUnmountVolumeCommands(volumeNumber, mountpoint));
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().unmountVolume(volumeNumber, mountpoint);
        logDebug(output);
    }
}
