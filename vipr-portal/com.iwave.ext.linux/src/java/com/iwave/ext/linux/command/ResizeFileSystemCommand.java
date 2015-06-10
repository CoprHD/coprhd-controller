/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

public class ResizeFileSystemCommand extends LinuxCommand {
    public ResizeFileSystemCommand(String device, boolean force) {
        setCommand(CommandConstants.RESIZE2FS);
        if (force) {
            addArgument("-f");
        }
        addArgument(device);
        setRunAsRoot(true);
    }
}
