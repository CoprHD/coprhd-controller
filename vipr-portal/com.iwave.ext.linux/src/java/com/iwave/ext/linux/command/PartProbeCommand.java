/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.command.CommandException;



/**
 * Tell the OS to rescan the partition map for a device.
 */
public class PartProbeCommand extends LinuxCommand {
    
    public PartProbeCommand(String device) {
        setCommand("partprobe " + device);
        setRunAsRoot(true);
    }
    
    @Override
    protected void processError() throws CommandException {
        // we want the order to proceed if even if this command fails, so just log the error.
        this.log.warn("parprobe command failed: "+getErrorMessage());
    }
    
}
