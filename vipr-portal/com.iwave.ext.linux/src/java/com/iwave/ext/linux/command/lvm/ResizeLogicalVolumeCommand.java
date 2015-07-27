/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class ResizeLogicalVolumeCommand extends LinuxCommand {
    public ResizeLogicalVolumeCommand(String logicalVolume) {
        this(logicalVolume, "100%VG");
    }

    public ResizeLogicalVolumeCommand(String logicalVolume, String extent) {
        setCommand(CommandConstants.LVRESIZE);
        addArguments("-l", extent);
        addArgument(logicalVolume);
        setRunAsRoot(true);
    }
}
