/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;


public class RescanAllHBAsCommand extends LinuxCommand {
    private static final String SLEEP_TIME = "sleepTime";

    public RescanAllHBAsCommand() {
        StringBuilder sb = new StringBuilder();
        sb.append("for i in /sys/class/fc_host/host*/issue_lip; do ");
        sb.append("  echo 1 > $i; ");
        sb.append("done; ");
        sb.append("sleep ${sleepTime}; ");
        sb.append("for i in /sys/class/scsi_host/host*/scan; do ");
        sb.append("  echo \"- - -\" > $i; ");
        sb.append("done; ");
        setCommand(sb.toString());
        // Default sleep time to 5s
        setSleepTime(5);
        setRunAsRoot(true);
    }

    public void setSleepTime(int sleepTime) {
        setVariableValue(SLEEP_TIME, String.valueOf(sleepTime));
    }
}
