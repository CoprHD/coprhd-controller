/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

/**
 * Wraps the linux LVM lvcreate command to create a Logical Volume.
 * 
 * @author Chris Dail
 */
public class LVCreateCommand extends LinuxCommand {
    public static final String GROUP = "group";
    
    public LVCreateCommand() {
        setCommand(CommandConstants.LVCREATE);
        addVariable(GROUP);
        setRunAsRoot(true);
    }
    
    public void setVolumeGroup(String group) {
        setVariableValue(GROUP, group);
    }
    
    public void setFullExtent() {
        setExtents("100%VG");
    }
    
    public void setExtents(String extents) {
        addArguments("-l", extents);
    }
    
    public void setName(String name) {
        addArguments("-n", name);
    }
}
