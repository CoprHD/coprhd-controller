/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.emc.aix.command.MountCommand;

public class MountPath extends AixExecutionTask<Void> {
    private String path;
    
    public MountPath(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        MountCommand command = new MountCommand(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
