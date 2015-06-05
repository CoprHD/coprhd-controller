/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class DeletePhysicalVolumeCommand extends LinuxCommand {
    public DeletePhysicalVolumeCommand(String physicalVolume) {
        setCommand(CommandConstants.PVREMOVE);
        addArgument(physicalVolume);
        setRunAsRoot(true);
    }
}
