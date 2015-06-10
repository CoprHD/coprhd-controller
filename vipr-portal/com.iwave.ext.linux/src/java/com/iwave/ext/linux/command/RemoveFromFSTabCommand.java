/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class RemoveFromFSTabCommand extends LinuxCommand {
    private static final String MOUNT_POINT = "mountPoint";

    public RemoveFromFSTabCommand(String mountPoint) {
        this();
        setMountPoint(mountPoint);
    }

    public RemoveFromFSTabCommand() {
        setCommand("sed -i \"/${mountPoint}\\s/d\" /etc/fstab");
        setRunAsRoot(true);
    }

    public void setMountPoint(String mountPoint) {
        // escape special characters
        mountPoint = mountPoint.replaceAll("([^\\w])", "\\\\$1");
        setVariableValue(MOUNT_POINT, mountPoint);
    }
}
