/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.MountCommand;

public class MountPath extends HpuxExecutionTask<Void> {
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
