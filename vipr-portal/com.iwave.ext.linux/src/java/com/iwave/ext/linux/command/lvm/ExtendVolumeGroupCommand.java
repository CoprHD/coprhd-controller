/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import java.util.Set;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class ExtendVolumeGroupCommand extends LinuxCommand {
    public ExtendVolumeGroupCommand(String volumeGroup, Set<String> physicalVolumes) {
        setCommand(CommandConstants.VGEXTEND);
        addArgument(volumeGroup);
        for (String physicalVolume : physicalVolumes) {
            addArgument(physicalVolume);
        }
        setRunAsRoot(true);
    }
}
