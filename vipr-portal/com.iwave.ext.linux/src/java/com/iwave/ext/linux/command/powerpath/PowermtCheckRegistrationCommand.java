/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.powerpath;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;

public class PowermtCheckRegistrationCommand extends PowermtCommand {
    private static final String EXPIRED = "Expired:";

    public PowermtCheckRegistrationCommand() {
        super();
        addArgument("check_registration");
    }

    @Override
    protected void processOutput() throws CommandException {
        String stdout = getOutput().getStdout();
        if (StringUtils.contains(stdout, EXPIRED)) {
            throw new PowerPathException("PowerPath license has expired.", getOutput());
        }
        super.processOutput();
    }

    @Override
    protected void processError() throws CommandException {
        throw new PowerPathException("PowerPath is not on the path or not installed.", getOutput());
    }
}
