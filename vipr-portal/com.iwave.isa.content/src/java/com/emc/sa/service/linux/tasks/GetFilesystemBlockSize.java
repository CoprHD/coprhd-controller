/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.fdisk.GetFilesystemBlockSizeCommand;

/**
 * Linux task for getting the block size of the filesystem device
 * 
 */
public class GetFilesystemBlockSize extends LinuxExecutionTask<String> {

    private String device;

    public GetFilesystemBlockSize(String device) {
        this.device = device;
    }

    @Override
    public String executeTask() throws Exception {
        GetFilesystemBlockSizeCommand command = new GetFilesystemBlockSizeCommand(device);
        return executeCommand(command, SHORT_TIMEOUT);
    }
}
