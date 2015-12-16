/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.hpux.command;

import org.apache.commons.lang.StringUtils;

import com.emc.hpux.model.HpuxVersion;
import com.iwave.ext.command.CommandException;

public class GetHpuxVersionCommand extends HpuxVersionCommand {

    public GetHpuxVersionCommand() {
        setCommand("uname -r");
    }

    @Override
    public void parseOutput() {
        String stdOut = getOutput().getStdout();
        results = new HpuxVersion(stdOut);
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
