/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class MountVolume extends DiskPartScript<Void> {
    private int volumeNumber;
    private String mountpoint;

    public MountVolume(int volumeNumber, String mountpoint) {
        this.volumeNumber = volumeNumber;
        this.mountpoint = mountpoint;
        setDiskPartCommands(WindowsUtils.getMountVolumeCommands(volumeNumber, mountpoint));
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().mountVolume(volumeNumber, mountpoint);
        logDebug(output);
    }
}
