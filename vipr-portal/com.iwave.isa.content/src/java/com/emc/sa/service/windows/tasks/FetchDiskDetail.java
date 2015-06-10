/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;
import com.iwave.ext.windows.model.Disk;

public class FetchDiskDetail extends DiskPartScript<Disk> {
    private int diskNumber;

    public FetchDiskDetail(int diskNumber) {
        this.diskNumber = diskNumber;
        setDiskPartCommands(WindowsUtils.getDetailDiskCommands(diskNumber));
    }

    @Override
    public Disk executeTask() throws Exception {
        return getTargetSystem().detailDisk(diskNumber);
    }
}
