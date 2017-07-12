/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.RemoveFromFSTabCommand;

public class RemoveFromFSTab extends HpuxExecutionTask<Void> {
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
