/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import com.iwave.ext.linux.command.RescanBlockDevice;

public class RescanBlockDevices extends LinuxExecutionTask<Void> {

    private List<String> devices;

    public RescanBlockDevices(List<String> devices) {
        this.devices = devices;
    }

    @Override
    public void execute() throws Exception {
        RescanBlockDevice command = new RescanBlockDevice();
        command.setDevices(devices);
        executeCommand(command, SHORT_TIMEOUT);
    }
}
