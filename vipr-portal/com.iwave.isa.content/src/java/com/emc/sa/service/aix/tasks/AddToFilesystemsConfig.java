/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.emc.aix.command.AddToFilesystemsCommand;

public class AddToFilesystemsConfig extends AixExecutionTask<Void> {

    private String device;
    private String mountPoint;
    private String fsType;

    public AddToFilesystemsConfig(String device, String mountPoint, String fsType) {
        this.device = device;
        this.mountPoint = mountPoint;
        this.fsType = fsType;
    }

    @Override
    public void execute() throws Exception {
        AddToFilesystemsCommand command = new AddToFilesystemsCommand();
        command.setOptions(device, mountPoint, fsType);
        executeCommand(command, SHORT_TIMEOUT);
    }
}