/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

/**
 * Wraps the linux LVM pvcreate command to create a physical volume.
 * 
 * @author Chris Dail
 */
public class PVCreateCommand extends LinuxCommand {
    public static final String DEVICE = "device";
    
    public PVCreateCommand() {
        setCommand(CommandConstants.PVCREATE);
        addVariable(DEVICE);
        setRunAsRoot(true);
    }
    
    public void setDevice(String device) {
        setVariableValue(DEVICE, device);
    }
}
