/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.hmc.command;

import org.apache.commons.lang.StringUtils;

import com.emc.hmc.model.HMCVersion;
import com.iwave.ext.command.CommandException;

public class GetHMCVersionCommand extends HMCVersionCommand {

    public GetHMCVersionCommand() {
        setCommand("/usr/bin/oslevel");
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        results = new HMCVersion(stdOut);
    }

    @Override
    protected void processError() throws CommandException {
        String stdout = StringUtils.trimToEmpty(getOutput().getStdout());
        String stderr = StringUtils.trimToEmpty(getOutput().getStderr());
        if (stdout.isEmpty() || !stderr.isEmpty()) {
            super.processError();
        }
    }
}
