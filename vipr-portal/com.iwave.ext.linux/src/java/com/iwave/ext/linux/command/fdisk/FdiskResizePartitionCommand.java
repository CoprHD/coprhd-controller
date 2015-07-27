/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.fdisk;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;


/**
 * Delete the primary partition map from a storage device and create a new one
 */
public class FdiskResizePartitionCommand extends LinuxCommand {
    
    public FdiskResizePartitionCommand(String device) {
        StringBuilder sb = new StringBuilder();
        sb.append("echo -e \"d\nn\np\n1\n\n\nw\" | " + CommandConstants.FDISK + " ").append(device);
        setCommand(sb.toString());
        setRunAsRoot(true);
    }
    /*
     * d = delete partition (chooses first partition by default)
     * n = create new partition 
     *   p = partition type 'primary'
     *   1 = new partition id
     *     = use default partition start
     *     = use default partition end, filling the volume
     * w = write changes to disk
     */
    
    
    @Override
    protected void processError() throws CommandException {
        if (getOutput().getStdout().contains("WARNING: Re-reading the partition table failed with error 22")) {
            return;
        }
        super.processError();
    }
}
