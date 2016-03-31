/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.DeleteDirectoryCommand;

/**
 */
public class DeleteDirectory extends HpuxExecutionTask<Void> {
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
