/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;
import com.iwave.ext.command.CommandOutput;

public class MultipathCommand extends LinuxCommand {
    
    private static final String DM_NOT_LOADED = "DM multipath kernel driver not loaded";
    private static final String DM_NOT_INSTALLED = "No such file or directory";
    
    public MultipathCommand() {
        setCommand(CommandConstants.MULTIPATH);
        setRunAsRoot(true);
    }

    @Override
    protected void processError() throws CommandException {
        CommandOutput output = getOutput();
        if (isNotLoaded(output)) {
            throw new MultipathException("multipath kernel driver is not loaded", output);
        }
        if (isNotInstalled(output)) {
            throw new MultipathException("multipath is not installed", output);
        }
        super.processError();
    }

    protected boolean isNotInstalled(CommandOutput output) {
        return StringUtils.contains(output.getStdout(), DM_NOT_INSTALLED) || 
                StringUtils.contains(output.getStderr(), DM_NOT_INSTALLED);
    }

    protected boolean isNotLoaded(CommandOutput output) {
        return StringUtils.contains(output.getStdout(), DM_NOT_LOADED) || 
                StringUtils.contains(output.getStderr(), DM_NOT_LOADED);
    }
    
}
