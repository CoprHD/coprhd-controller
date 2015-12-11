/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.util.List;

import com.emc.hpux.command.ListRDisksCommand;
import com.emc.hpux.model.RDisk;
import com.emc.sa.service.aix.tasks.HDiskNotFoundException;
import com.emc.sa.util.VolumeWWNUtils;
import com.emc.storageos.model.block.BlockObjectRestRep;
import com.iwave.ext.command.CommandException;

public class FindRDiskForVolume extends RetryableCommandTask<String, CommandException> {

    private boolean usePowerPath;
    private BlockObjectRestRep volume;

    public FindRDiskForVolume(BlockObjectRestRep volume, boolean usePowerPath) {
        this.usePowerPath = usePowerPath;
        this.volume = volume;
    }

    @Override
    protected String tryExecute() {
        List<RDisk> devices = executeCommand(new ListRDisksCommand());
        for (RDisk device : devices) {
            if (VolumeWWNUtils.wwnMatches(device.getWwn(), volume)) {
                return device.getPath();
            }
        }

        // could not find associated hdisk for volume
        throw new RDiskNotFoundException(volume.getWwn());
    }

    @Override
    protected boolean canRetry(CommandException e) {
        return e instanceof HDiskNotFoundException;
    }
}