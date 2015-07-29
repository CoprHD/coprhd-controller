/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class RescanDevicesCommand extends LinuxCommand {
    public RescanDevicesCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for host in `ls /sys/class/fc_host`; do ");
        sb.append("  echo 1 > /sys/class/fc_host/$host/issue_lip; ");
        sb.append("done; ");

        sb.append("for host in `ls /sys/class/scsi_host`; do ");
        sb.append("  echo \"- - -\" > /sys/class/scsi_host/$host/scan; ");
        sb.append("done; ");
        setCommand(sb.toString());
        setRunAsRoot(true);
    }
}
