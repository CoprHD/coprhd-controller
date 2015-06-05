/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.linux.command.CatCommand;

/**
 * attempt to find the device of the first partition on the disk.
 */
public class GetPrimaryPartitionDeviceMultiPath extends LinuxExecutionTask<String> {
    
    private final static String DM_NAME_PATH = "/sys/block/%s/holders/dm-*/dm/name";
    private final static String DM_NAME_SUFFIX = "p1";

    private final String device;
    private final String dmname;
    
    public GetPrimaryPartitionDeviceMultiPath(String device, String dmname) {
        this.device = device;
        this.dmname = dmname;
    }
    
    @Override
    public String executeTask() throws Exception {
        String partitionDevice = StringUtils.EMPTY;

        String partitionNameFile = String.format(DM_NAME_PATH, dmname);
        
        // try to cat the name file
        try {
            partitionDevice = executeCommand(new CatCommand(partitionNameFile)).trim();
        }
        catch (RuntimeException e) {
            // don't let the exception bubble up. We'll handle it here.
            logWarn("primary.partition.multipath.unusable", partitionNameFile);
        }
        
        if (StringUtils.isBlank(partitionDevice)) {
            // if we were unable to find the device name above, we can try to assume the device name is <device>p1
            partitionDevice = StringUtils.substringAfterLast(device, "/")+DM_NAME_SUFFIX;
        }
        
        logInfo("primary.partition.multipath.using", partitionDevice);
        return StringUtils.substringBeforeLast(device, "/")+"/"+partitionDevice;

    }
    
}
