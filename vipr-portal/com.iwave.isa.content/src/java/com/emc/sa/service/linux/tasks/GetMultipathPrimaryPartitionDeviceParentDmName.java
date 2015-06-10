/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.linux.command.FindParentMultipathDeviceCommand;

/**
 * attempt to find the parent device using the device of the first partition on the disk.
 */
public class GetMultipathPrimaryPartitionDeviceParentDmName extends LinuxExecutionTask<String> {

    private final String device;
    
    public GetMultipathPrimaryPartitionDeviceParentDmName(String device) {
        this.device = device;
    }
    
    @Override
    public String executeTask() throws Exception {
        String partitionDeviceName = StringUtils.substringAfterLast(device, "/");
        String parentDeviceDmName = executeCommand(new FindParentMultipathDeviceCommand(partitionDeviceName)).trim();
        if (StringUtils.isBlank(parentDeviceDmName)) {
            throw stateException("GetMultipathPrimaryPartitionDeviceParentDmName.illegalState.unableToFindDevice", device);
        }
        return parentDeviceDmName;
    }
    
}
