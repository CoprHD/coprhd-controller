/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.RemoveFromFSTabCommand;

/**
 * Task to remove entry from fstab file
 * @author root
 *
 */
public class RemoveFromFSTab extends HpuxExecutionTask<Void> {
    private String path;

    /**
     * Remove from fstab constructor
     * @param path {@link String} path to be removed from fstab
     */
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