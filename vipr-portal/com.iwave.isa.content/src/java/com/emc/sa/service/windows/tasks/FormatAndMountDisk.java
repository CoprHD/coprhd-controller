/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;
import com.iwave.ext.windows.model.wmi.DiskDrive;

public class FormatAndMountDisk extends DiskPartScript<Void> {
    private static final String NOT_ASSIGNED_ERROR_MESSAGE = "The specified drive letter is not free to be assigned";

    private int diskNumber;
    private String fsType;
    private String allocationUnitSize;
    private String label;
    private String mountpoint;
    private String partitionType;

    public FormatAndMountDisk(DiskDrive disk, String fsType, String allocationUnitSize, String label, String mountpoint,
            String partitionType) {
        this(disk.getNumber(), fsType, allocationUnitSize, label, mountpoint, partitionType);
    }

    public FormatAndMountDisk(int diskNumber, String fsType, String allocationUnitSize, String label, String mountpoint,
            String partitionType) {
        this.diskNumber = diskNumber;
        this.fsType = fsType;
        this.label = label;
        this.allocationUnitSize = allocationUnitSize;
        this.mountpoint = mountpoint;
        this.partitionType = partitionType;
        setDiskPartCommands(WindowsUtils.getFormatAndMountDiskCommands(diskNumber, fsType, allocationUnitSize, label, mountpoint,
                partitionType));
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().formatAndMountDisk(diskNumber, fsType, allocationUnitSize, label, mountpoint, partitionType);
        logDebug(output);
        if (output.contains(NOT_ASSIGNED_ERROR_MESSAGE)) {
            throw stateException("illegalState.FormateAndMountDisk.driveLetterUnavailable");
        }
    }
}
