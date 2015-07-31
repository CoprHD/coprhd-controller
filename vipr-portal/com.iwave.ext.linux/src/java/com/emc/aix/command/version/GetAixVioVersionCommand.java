/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.aix.command.version;

import org.apache.commons.lang.StringUtils;

import com.emc.aix.model.AixVersion;
import com.iwave.ext.command.CommandException;

public class GetAixVioVersionCommand extends AixVersionCommand {

    public GetAixVioVersionCommand() {
        setCommand("ioslevel");
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        results = new AixVersion(stdOut);
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