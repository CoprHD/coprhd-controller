/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;



/**
 * Tell the OS to rescan the partition map for a device.
 */
public class KpartxCommand extends LinuxCommand {
    
    public KpartxCommand(String device) {
        setCommand("kpartx " + device);
        setRunAsRoot(true);
    }
    
}
