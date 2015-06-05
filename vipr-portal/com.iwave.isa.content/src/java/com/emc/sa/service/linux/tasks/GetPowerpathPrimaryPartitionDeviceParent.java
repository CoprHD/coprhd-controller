/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.linux.command.FindParentPowerpathDeviceNameCommand;

/**
 * attempt to find the parent device using the device of the first partition on the disk.
 */
public class GetPowerpathPrimaryPartitionDeviceParent extends LinuxExecutionTask<String> {

    private final String device;
    
    public GetPowerpathPrimaryPartitionDeviceParent(String device) {
        this.device = device;
    }
    
    @Override
    public String executeTask() throws Exception {
        String partitionDeviceName = StringUtils.substringAfterLast(device, "/");
        String parentDeviceName = executeCommand(new FindParentPowerpathDeviceNameCommand(partitionDeviceName)).trim();
        if (StringUtils.isBlank(parentDeviceName)) {
            throw stateException("GetPowerpathPrimaryPartitionDeviceParent.illegalState.unableToFindDevice", device);
        }
        return StringUtils.substringBeforeLast(device, "/")+"/"+parentDeviceName;
    }
    
}
