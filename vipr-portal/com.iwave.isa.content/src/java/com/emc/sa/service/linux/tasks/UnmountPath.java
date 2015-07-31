/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.UnmountCommand;

public class UnmountPath extends LinuxExecutionTask<Void> {

    private String path;

    public UnmountPath(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        UnmountCommand command = new UnmountCommand();
        command.setPath(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
