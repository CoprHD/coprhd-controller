/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command;

public class RemoveFromFilesystemsCommand extends AixCommand {
    private static final String MOUNT_POINT = "mountPoint";

    public RemoveFromFilesystemsCommand(String mountPoint) {
        this();
        setMountPoint(mountPoint);
    }

    public RemoveFromFilesystemsCommand() {
        setCommand("sed '/${mountPoint}:/,/^$/d' /etc/filesystems | if test -t 0; then echo \"pattern not found\"; else echo \"$(cat -)\" > /etc/filesystems; fi");
        setRunAsRoot(true);
    }

    public void setMountPoint(String mp) {
        String mountPoint = mp;
        // escape special characters
        mountPoint = mountPoint.replaceAll("([^\\w])", "\\\\$1");
        setVariableValue(MOUNT_POINT, mountPoint);
    }
}
