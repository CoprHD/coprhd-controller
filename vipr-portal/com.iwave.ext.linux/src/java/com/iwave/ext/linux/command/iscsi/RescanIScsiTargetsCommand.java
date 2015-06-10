/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.iscsi;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxCommand;

public class RescanIScsiTargetsCommand extends LinuxCommand {
    private static final String NO_SESSIONS = "No session found";

    public RescanIScsiTargetsCommand() {
        setCommand(CommandConstants.ISCSIADM);
        addArgument("--mode node --rescan");
        setRunAsRoot(true);
    }

    @Override
    protected void processError() throws CommandException {
        if (!containsInOutputIgnoreCase(NO_SESSIONS)) {
            super.processError();
        }
    }
}
