/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.DeleteDirectoryCommand;

/**
 */
public class DeleteDirectory extends LinuxExecutionTask<Void> {
    private String directory;

    public DeleteDirectory(String directory) {
        this.directory = directory;
    }

    @Override
    public void execute() {
        executeCommand(new DeleteDirectoryCommand(directory, true));
    }
}
