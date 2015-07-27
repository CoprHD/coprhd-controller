/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import com.iwave.ext.linux.command.LinuxCommand;

/**
 * Wraps the linux mke2fs command to format a disk.
 * 
 * @author Chris Dail
 */
public class Mke2fsCommand extends LinuxCommand {
    public static final String DEVICE = "device";
    
    public Mke2fsCommand() {
        setCommand(CommandConstants.MKE2FS);
        addVariable(DEVICE);
        setRunAsRoot(true);
    }
    
    public void setDevice(String device) {
        setVariableValue(DEVICE, device);
    }
    
    public void setJournaling() {
        addArguments("-j");
    }
    
    public void setType(String type) {
        addArguments("-t", type);
    }
}
