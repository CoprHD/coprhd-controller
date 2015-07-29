/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Collection;

import org.apache.commons.lang.text.StrBuilder;

import com.iwave.ext.command.CommandException;

public class RescanBlockDevice extends LinuxCommand {
    private static final String DEVICES = "devices";

    public RescanBlockDevice() {
        StringBuilder sb = new StringBuilder();
        sb.append("for device in ${devices}; do ");
        sb.append("  echo 1 > /sys/block/$device/device/rescan; ");
        sb.append("done; ");
        setCommand(sb.toString());
        setRunAsRoot(true);
    }

    public void setDevices(Collection<String> hostIds) {
        StrBuilder sb = new StrBuilder();
        for (String hostId : hostIds) {
            sb.appendSeparator(' ');
            sb.append(hostId);
        }
        setVariableValue(DEVICES, sb.toString());
    }

    @Override
    protected void validateCommandLine() throws CommandException {
        requireVariableValues(DEVICES);
    }
}
