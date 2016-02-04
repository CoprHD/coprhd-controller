/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.MakeFilesystemCommand;

public class MakeFilesystem extends HpuxExecutionTask<Void> {
    private String path;

    public MakeFilesystem(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        MakeFilesystemCommand command = new MakeFilesystemCommand(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
