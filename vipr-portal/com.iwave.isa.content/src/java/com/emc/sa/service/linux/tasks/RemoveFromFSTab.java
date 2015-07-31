/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.RemoveFromFSTabCommand;

public class RemoveFromFSTab extends LinuxExecutionTask<Void> {
    private String path;

    public RemoveFromFSTab(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        RemoveFromFSTabCommand command = new RemoveFromFSTabCommand();
        command.setMountPoint(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
