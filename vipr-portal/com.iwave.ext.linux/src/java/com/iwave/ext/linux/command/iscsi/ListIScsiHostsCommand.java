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
import com.iwave.ext.linux.command.parser.IScsiHostParser;
import com.iwave.ext.linux.model.IScsiHost;

public class ListIScsiHostsCommand extends LinuxResultsCommand<List<IScsiHost>> {

    public ListIScsiHostsCommand() {
        setCommand(CommandConstants.ISCSIADM);
        addArgument("--mode host -P2");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            results = new IScsiHostParser().parseHosts(stdout);
        }
        else {
            results = Lists.newArrayList();
        }
    }

    @Override
    protected void processError() throws CommandException {
        String stderr = getOutput().getStderr();
        if (StringUtils.contains(stderr, "No iSCSI hosts")) {
            results = Lists.newArrayList();
        }
        else {
            super.processError();
        }
    }
}
