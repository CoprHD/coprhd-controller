/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.iwave.ext.linux.command.AddToFSTabCommand;

public class AddToFSTab extends HpuxExecutionTask<Void> {

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
