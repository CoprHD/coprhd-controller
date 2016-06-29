/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import java.util.List;

import com.emc.aix.command.ListHDisksCommand;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.model.PowerPathDevice;
import com.iwave.ext.linux.util.VolumeWWNUtils;

public class FindHDiskForVolume extends RetryableCommandTask<String, CommandException> {

    private boolean usePowerPath;
    private BlockObjectRestRep volume;

    public FindHDiskForVolume(BlockObjectRestRep volume, boolean usePowerPath) {
        this.usePowerPath = usePowerPath;
        this.volume = volume;
    }

    @Override
    protected String tryExecute() {
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

        // could not find associated hdisk for volume
        throw new HDiskNotFoundException(volume.getWwn());
    }

    @Override
    protected boolean canRetry(CommandException e) {
        return e instanceof HDiskNotFoundException;
    }
}