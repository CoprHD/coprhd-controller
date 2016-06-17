/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.MountCommand;

public class MountNFSPath extends LinuxExecutionTask<Void> {
    private String path;
    private String security;

    public MountNFSPath(String path, String security) {
        this.path = path;
        this.security = security;
    }

    @Override
    public void execute() throws Exception {
        MountCommand command = new MountCommand();
        command.enableOptions();
        command.setSecurity(security);
        command.setPath(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
