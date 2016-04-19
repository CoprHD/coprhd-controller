/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.MkdirCommand;

public class CreateDirectory extends HpuxExecutionTask<Void> {

    private String path;

    public CreateDirectory(String path) {
        this.path = path;
    }

    @Override
    public void execute() throws Exception {
        MkdirCommand command = new MkdirCommand(true);
        command.setDir(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}