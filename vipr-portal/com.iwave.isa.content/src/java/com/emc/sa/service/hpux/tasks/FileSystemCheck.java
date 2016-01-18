/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.FsckCommand;

public class FileSystemCheck extends HpuxExecutionTask<Void> {
    private String rdisk;

    public FileSystemCheck(String rdisk) {
        this.rdisk = rdisk;
    }

    @Override
    public void execute() throws Exception {
        FsckCommand command = new FsckCommand(rdisk);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
