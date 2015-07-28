/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.linux.command.FindPowerpathBlockDevicesCommand;

public class GetPowerpathBlockDevices extends LinuxExecutionTask<List<String>> {

    private final String device;

    public GetPowerpathBlockDevices(String device) {
        this.device = device;
    }

    @Override
    public List<String> executeTask() throws Exception {
        String partitionDeviceName = StringUtils.substringAfterLast(device, "/");
        return executeCommand(new FindPowerpathBlockDevicesCommand(partitionDeviceName));
    }

}
