/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import com.iwave.ext.linux.command.FindMultipathBlockDevicesCommand;

public class GetMultipathBlockDevices extends LinuxExecutionTask<List<String>> {

    private final String device;
    
    public GetMultipathBlockDevices(String device) {
        this.device = device;
    }
    
    @Override
    public List<String> executeTask() throws Exception {
        return executeCommand(new FindMultipathBlockDevicesCommand(device));
    }
    
}
