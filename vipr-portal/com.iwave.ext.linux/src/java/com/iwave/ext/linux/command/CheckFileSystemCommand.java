/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class CheckFileSystemCommand extends LinuxCommand {
    public CheckFileSystemCommand(String device, boolean force) {
        setCommand(CommandConstants.E2FSCK);
        addArgument("-y");
        if (force) {
            addArgument("-f");
        }
        addArgument(device);
        setRunAsRoot(true);
    }
}
