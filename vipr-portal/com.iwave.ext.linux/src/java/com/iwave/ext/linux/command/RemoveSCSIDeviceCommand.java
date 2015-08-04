/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class RemoveSCSIDeviceCommand extends LinuxCommand {
    private static final String COMMAND = "echo \"scsi remove-single-device %s %s %s %s\" > /proc/scsi/scsi";

    public RemoveSCSIDeviceCommand(String host, String channel, String id, String lun) {
        setCommand(String.format(COMMAND, host, channel, id, lun));
        setRunAsRoot(true);
    }
}
