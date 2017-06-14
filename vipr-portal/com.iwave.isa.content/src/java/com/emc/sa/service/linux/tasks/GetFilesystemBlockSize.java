/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.fdisk.GetFilesystemBlockSizeCommand;

public class GetFilesystemBlockSize extends LinuxExecutionTask<String> {

    private String path;

    public GetFilesystemBlockSize(String path) {
        this.path = path;
    }

    @Override
    public String executeTask() throws Exception {
        GetFilesystemBlockSizeCommand command = new GetFilesystemBlockSizeCommand(path);
        return executeCommand(command, SHORT_TIMEOUT);
    }
}
