/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;



/**
 * Tell the multipath daemon to resize the path to the given device
 */
public class MultipathdResizeCommand extends LinuxCommand {
    
    public MultipathdResizeCommand(String device) {
        String deviceName = device.substring(device.lastIndexOf("/") + 1);
        setCommand(String.format("multipathd -k'resize map %s'", deviceName));
        setRunAsRoot(true);
    }
    
}
