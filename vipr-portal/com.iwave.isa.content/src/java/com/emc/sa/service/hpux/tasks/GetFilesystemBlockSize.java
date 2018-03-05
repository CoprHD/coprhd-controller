/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.GetFilesystemBlockSizeCommand;

/**
 * HP-UX task for getting the block size of the filesystem device
 * 
 */
public class GetFilesystemBlockSize extends HpuxExecutionTask<String> {

    private String device;

    /**
     * Constructor get file system block size 
     * @param device {@link String} device
     */
    public GetFilesystemBlockSize(String device) {
        this.device = device;
    }

    @Override
    public String executeTask() throws Exception {
        GetFilesystemBlockSizeCommand command = new GetFilesystemBlockSizeCommand(device);
        return executeCommand(command, SHORT_TIMEOUT);
    }
}