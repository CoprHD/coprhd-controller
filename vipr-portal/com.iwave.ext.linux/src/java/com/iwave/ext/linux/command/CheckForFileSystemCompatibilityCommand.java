/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.command.CommandException;

public class CheckForFileSystemCompatibilityCommand extends LinuxCommand {

    public CheckForFileSystemCompatibilityCommand() {
        setCommand("cat /proc/filesystems /etc/filesystems | grep ");
    }

    public void setFileSystemType(String fsType) {
        addArgument(fsType);
    }

    @Override
    protected void processError() throws CommandException {
        getOutput().setStderr(getErrorMessage());
    }

}
