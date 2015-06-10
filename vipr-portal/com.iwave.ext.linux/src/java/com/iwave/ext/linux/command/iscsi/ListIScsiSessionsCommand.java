/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.iscsi;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandException;
import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.linux.command.parser.IScsiSessionParser;
import com.iwave.ext.linux.model.IScsiSession;

public class ListIScsiSessionsCommand extends LinuxResultsCommand<List<IScsiSession>> {

    public ListIScsiSessionsCommand() {
        setCommand(CommandConstants.ISCSIADM);
        addArgument("--mode session -P1");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            results = new IScsiSessionParser().parseSessions(stdout);
        }
        else {
            results = Lists.newArrayList();
        }
    }
    
    @Override
    protected void processError() throws CommandException {
        String stderr = getOutput().getStderr();
        if (StringUtils.contains(stderr, "No active sessions")) {
            results = Lists.newArrayList();
        }
        else {
            super.processError();
        }
    }
}
