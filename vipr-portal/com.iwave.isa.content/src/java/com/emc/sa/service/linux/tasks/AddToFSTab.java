/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.AddToFSTabCommand;

public class AddToFSTab extends LinuxExecutionTask<Void> {

    public static final String DEFAULT_OPTIONS = "defaults";

    private String device;
    private String path;
    private String fsType;
    private String options;

    public AddToFSTab(String device, String path, String fsType, String options) {
        this.device = device;
        this.path = path;
        this.fsType = fsType;
        this.options = options;
    }

    @Override
    public void execute() throws Exception {
        AddToFSTabCommand command = new AddToFSTabCommand();
        command.setOptions(device, path, fsType, options);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
