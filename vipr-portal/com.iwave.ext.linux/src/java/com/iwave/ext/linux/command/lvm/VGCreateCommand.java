/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.lvm;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

/**
 * Wraps the linux LVM vgcreate command to create a Volume Group.
 * 
 * @author Chris Dail
 */
public class VGCreateCommand extends LinuxCommand {
    public static final String GROUP = "group";
    public static final String DEVICE = "device";

    public VGCreateCommand() {
        setCommand(CommandConstants.VGCREATE);
        addVariable(GROUP);
        addVariable(DEVICE);
        setRunAsRoot(true);
    }

    public void setGroup(String group) {
        setVariableValue(GROUP, group);
    }

    public void setDevice(String device) {
        setVariableValue(DEVICE, device);
    }
}
