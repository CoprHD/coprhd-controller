/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.iwave.ext.linux.command.UnmountCommand;

public class UnmountPath extends AixExecutionTask<Void> {

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