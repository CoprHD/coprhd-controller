/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.fdisk.FdiskResizePartitionCommand;

public class ResizePartition extends LinuxExecutionTask<Void> {

    private String device;

    public ResizePartition(String device) {
        this.device = device;
    }

    @Override
    public void execute() throws Exception {
        executeCommand(new FdiskResizePartitionCommand(device));
    }
}
