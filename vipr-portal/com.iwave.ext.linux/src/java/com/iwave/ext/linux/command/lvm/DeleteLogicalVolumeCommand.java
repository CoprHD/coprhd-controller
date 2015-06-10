/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxScriptCommand;

public class DeleteLogicalVolumeCommand extends LinuxScriptCommand {
    public DeleteLogicalVolumeCommand(String logicalVolume) {
        addCommandLine("%s -a n %s", CommandConstants.LVCHANGE, logicalVolume);
        addCommandLine("%s %s", CommandConstants.LVREMOVE, logicalVolume);
        setRunAsRoot(true);
    }
}
