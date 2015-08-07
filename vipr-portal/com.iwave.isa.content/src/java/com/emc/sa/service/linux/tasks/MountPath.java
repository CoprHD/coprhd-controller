/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.MountCommand;

public class MountPath extends LinuxExecutionTask<Void> {
    private String path;

    public MountPath(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        MountCommand command = new MountCommand();
        command.setPath(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
