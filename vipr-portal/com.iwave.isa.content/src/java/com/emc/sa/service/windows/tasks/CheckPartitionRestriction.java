/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import static com.iwave.ext.windows.WindowsUtils.isMBRCapacityInBytesTooLarge;

import com.emc.sa.engine.ExecutionUtils;
import com.iwave.ext.windows.model.DiskSummary;
import com.iwave.ext.windows.parser.ListDiskParser;
import com.iwave.ext.windows.WindowsUtils;

import java.util.List;

import org.apache.commons.lang.StringUtils;

public class CheckPartitionRestriction extends DiskPartScript<Void> {
    private int diskNumber;
    private long volumeSizeInBytes;

    private static String DISK_NUMBER_PREFIX = "Disk ";
    private static String GPT_FLAG = "*";

    public CheckPartitionRestriction(int diskNumber, long volumeSizeInBytes) {
        this.diskNumber = diskNumber;
        this.volumeSizeInBytes = volumeSizeInBytes;
        setDiskPartCommands(WindowsUtils.getListDiskCommands());
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().listDisk();
        logDebug(output);

        ListDiskParser parser = new ListDiskParser();
        List<DiskSummary> results = parser.parseDevices(output);

        validateResults(results);
    }

    private void validateResults(List<DiskSummary> disks) {
        String diskToValidate = DISK_NUMBER_PREFIX + diskNumber;

        for (DiskSummary disk : disks) {
            if (diskToValidate.equals(disk.getDiskNumber())) {
                if (!StringUtils.equals(disk.getGpt(), GPT_FLAG)) {
                    if (isMBRCapacityInBytesTooLarge(volumeSizeInBytes)) {
                        throw stateException("illegalState.CheckPartitionRestriction.restriction");
                    }

                }
            }
        }
    }
}
