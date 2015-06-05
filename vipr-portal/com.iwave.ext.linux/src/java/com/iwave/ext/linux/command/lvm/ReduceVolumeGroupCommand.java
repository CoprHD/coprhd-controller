/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import java.util.Collection;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxScriptCommand;

public class ReduceVolumeGroupCommand extends LinuxScriptCommand {
    public ReduceVolumeGroupCommand(String volumeGroup, Collection<String> physicalVolumes) {
        setCommand(CommandConstants.VGREDUCE);
        addArgument(volumeGroup);
        for (String physicalVolume : physicalVolumes) {
            addArgument(physicalVolume);
        }
        setRunAsRoot(true);
    }
}
