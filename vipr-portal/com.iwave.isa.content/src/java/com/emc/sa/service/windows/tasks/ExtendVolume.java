/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class ExtendVolume extends DiskPartScript<Void> {
    private String mountpoint;

    public ExtendVolume(String mountpoint) {
        this.mountpoint = mountpoint;
        setDiskPartCommands(WindowsUtils.getExtendVolumeCommands(mountpoint));
    }

    @Override
    public void execute() throws Exception {
       String output = getTargetSystem().extendVolume(mountpoint);
       logDebug(output);
    }
}
