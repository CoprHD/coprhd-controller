/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.List;

import com.emc.aix.command.ListHDisksCommand;
import com.emc.sa.util.VolumeWWNUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.linux.model.PowerPathDevice;

public class FindHDiskForVolume extends AixExecutionTask<String> {

    private boolean usePowerPath;
    private BlockObjectRestRep volume;

    public FindHDiskForVolume(BlockObjectRestRep volume, boolean usePowerPath) {
        this.usePowerPath = usePowerPath;
        this.volume = volume;
    }

    @Override
    public String executeTask() throws Exception {
        List<PowerPathDevice> devices = executeCommand(new ListHDisksCommand(usePowerPath, false));
        for (PowerPathDevice device : devices) {
            if (VolumeWWNUtils.wwnMatches(device.getWwn(), volume)) {
                return device.getDevice();
            }
        }

        devices = executeCommand(new ListHDisksCommand(usePowerPath, true));
        for (PowerPathDevice device : devices) {
            if (VolumeWWNUtils.wwnMatches(device.getWwn(), volume)) {
                return device.getDevice();
            }
        }

        return null;
    }
}