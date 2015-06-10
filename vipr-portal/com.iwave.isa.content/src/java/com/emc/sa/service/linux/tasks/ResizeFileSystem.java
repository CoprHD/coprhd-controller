/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.ResizeFileSystemCommand;

public class ResizeFileSystem extends LinuxExecutionTask<Void> {
    private String device;
    private boolean force;

    public ResizeFileSystem(String logicalVolume) {
        this(logicalVolume, false);
    }

    public ResizeFileSystem(String logicalVolume, boolean force) {
        this.device = logicalVolume;
        this.force = force;
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new ResizeFileSystemCommand(device, force), LONG_TIMEOUT);
    }
}
