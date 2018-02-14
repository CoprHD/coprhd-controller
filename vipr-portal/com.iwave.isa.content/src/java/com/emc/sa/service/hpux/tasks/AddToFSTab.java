/*
 * Copyright (c) 2018 Dell EMC
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import com.emc.hpux.command.AddToFSTabCommand;

/**
 * Task to add entry to fstab file
 * @author root
 *
 */
public class AddToFSTab extends HpuxExecutionTask<Void> {

    public static final String DEFAULT_OPTIONS = "defaults";
    public static final String DEFAULT_FS_TYPE = "vxfs";

    private String device;
    private String path;
    private String fsType;
    private String options;

    /**
     * Add to fstab constructor
     * @param device {@link String} device to add
     * @param path {@link String} mount point to add
     * @param fsType {@link String} fs type
     * @param options {@link String} fstab options 
     */
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