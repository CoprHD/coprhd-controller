/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.CheckFileSystemCommand;

public class CheckFileSystem extends LinuxExecutionTask<Void> {
    private String device;
    private boolean force;

    public CheckFileSystem(String device) {
        this(device, false);
    }

    public CheckFileSystem(String device, boolean force) {
        this.device = device;
        this.force = force;
        provideDetailArgs(device);
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new CheckFileSystemCommand(device, force), LONG_TIMEOUT);
    }
}
