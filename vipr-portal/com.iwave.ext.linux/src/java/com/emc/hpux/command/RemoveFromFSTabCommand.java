/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

public class RemoveFromFSTabCommand extends HpuxCommand {
    private static final String MOUNT_POINT = "mountPoint";

    public RemoveFromFSTabCommand(String mountPoint) {
        this();
        setMountPoint(mountPoint);
    }

    public RemoveFromFSTabCommand() {
        setCommand("sed \"/${mountPoint}/d\" /etc/fstab > /etc/fstab");
        setRunAsRoot(true);
    }

    public void setMountPoint(String mountPoint) {
        // escape special characters
        mountPoint = mountPoint.replaceAll("([^\\w])", "\\\\$1");
        setVariableValue(MOUNT_POINT, mountPoint);
    }
}
