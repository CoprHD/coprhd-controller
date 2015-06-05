/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command;

/**
 * Executes an 'echo' command to add an entry to the /etc/fstab file. Commands are in the format:
 * 
 * echo -e "DEVICE\tMNT\t\t\tFSTYPE\tOPTIONS\t0 0" >> /etc/fstab
 * 
 * @author Chris Dail
 */
public class AddToFSTabCommand extends LinuxCommand {
    public AddToFSTabCommand() {
        setCommand("echo");
        addArgument("-e");
        setRunAsRoot(true);
    }

    public void setOptions(String device, String mountPt, String fsType, String options) {
        StringBuilder sb = new StringBuilder();
        sb.append(device).append("\\\t");
        sb.append(mountPt).append("\\\t\\\t\\\t");
        sb.append(fsType).append("\\\t");
        sb.append(options).append("\\\t0 0");

        addArguments(sb.toString(), ">>", "/etc/fstab");
    }

    public void setOptions(String device, String mountPt, String fsType) {
        setOptions(device, mountPt, fsType, "defaults");
    }
}
