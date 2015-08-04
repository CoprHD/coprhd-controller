/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import java.util.ArrayList;
import java.util.Collection;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxScriptCommand;

public class DeleteVolumeGroupCommand extends LinuxScriptCommand {
    public DeleteVolumeGroupCommand(String volumeGroup) {
        this(volumeGroup, new ArrayList<String>());
    }

    public DeleteVolumeGroupCommand(String volumeGroup, Collection<String> logicalVolumes) {
        for (String logicalVolume : logicalVolumes) {
            addCommandLine("%s -a n %s", CommandConstants.LVCHANGE, logicalVolume);
            addCommandLine("%s %s", CommandConstants.LVREMOVE, logicalVolume);
        }
        addCommandLine("%s -a n %s", CommandConstants.VGCHANGE, volumeGroup);
        addCommandLine("%s %s", CommandConstants.VGREMOVE, volumeGroup);
        setRunAsRoot(true);
    }
}
