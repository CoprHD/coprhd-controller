/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.iscsi;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class ISCSITargetLoginCommand extends LinuxCommand {
    public ISCSITargetLoginCommand() {
        setCommand(CommandConstants.ISCSIADM);
        addArguments("--mode", "node");
        addArguments("--login");
        setRunAsRoot(true);
    }

    public ISCSITargetLoginCommand(String portal) {
        this();
        setPortal(portal);
    }

    public void setPortal(String portal) {
        addArguments("--portal", quoteString(portal));
    }
}
