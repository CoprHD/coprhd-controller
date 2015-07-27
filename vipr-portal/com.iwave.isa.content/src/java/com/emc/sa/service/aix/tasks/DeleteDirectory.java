/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.iwave.ext.linux.command.DeleteDirectoryCommand;

/**
 */
public class DeleteDirectory extends AixExecutionTask<Void> {
    private String directory;

    public DeleteDirectory(String directory) {
        this.directory = directory;
        setName("DeleteDirectory.name");
    }

    @Override
    public void execute() {
        executeCommand(new DeleteDirectoryCommand(directory, true));
    }
}
