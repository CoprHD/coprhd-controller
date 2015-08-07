/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.command.CommandOutput;
import com.iwave.ext.linux.command.CheckForFileSystemCompatibilityCommand;

public class CheckForFileSystemCompatibility extends LinuxExecutionTask<Void> {

    private String fsType;

    public CheckForFileSystemCompatibility(String fsType) {
        this.fsType = fsType;
    }

    @Override
    public void execute() throws Exception {
        CheckForFileSystemCompatibilityCommand command = new CheckForFileSystemCompatibilityCommand();
        command.setFileSystemType(fsType);
        executeCommand(command, SHORT_TIMEOUT);

        CommandOutput output = command.getOutput();
        if (!output.getStdout().contains(fsType)) {
            throw stateException("CheckForFileSystemCompatibility.illegalState.fileSystemTypeUnsupported", fsType);
        }
    }
}
