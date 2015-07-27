/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.iwave.ext.command.CommandException;

public class FindMountPointCommand extends LinuxResultsCommand<String> {
    private static final Pattern FSTAB_ENTRY = Pattern.compile("[^\\s]+\\s+([^\\s]+)");
    private static final String DEVICE = "device";
    
    public FindMountPointCommand() {
        setCommand("grep");
        addVariable(DEVICE).addArgument("/etc/fstab");
    }
    
    public void setDevice(String device) {
        setVariableValue(DEVICE, quoteString(device));
    }
    
    @Override
    public void parseOutput() {
        Matcher m = FSTAB_ENTRY.matcher(getOutput().getStdout());
        if (m.find()) {
            results = m.group(1);
        }
    }
    
    @Override
    protected void processError() throws CommandException {
        if (getOutput().getExitValue() == 1) {
            results = null;
        }
        else {
            super.processError();
        }
    }
}
