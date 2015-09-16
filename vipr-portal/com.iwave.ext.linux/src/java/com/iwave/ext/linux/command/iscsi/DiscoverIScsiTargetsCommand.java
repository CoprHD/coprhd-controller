/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.iscsi;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;
import com.iwave.ext.linux.command.parser.DiscoverIScsiTargetsParser;
import com.iwave.ext.linux.model.IScsiTarget;

public class DiscoverIScsiTargetsCommand extends LinuxResultsCommand<List<IScsiTarget>> {
    private static final String PORTAL = "portal";

    public DiscoverIScsiTargetsCommand() {
        setCommand(CommandConstants.ISCSIADM);
        addArgument("--mode discovery --type sendtargets --portal");
        addVariable(PORTAL);
        setRunAsRoot(true);
    }

    public DiscoverIScsiTargetsCommand(String portal) {
        this();
        setPortal(portal);
    }

    public void setPortal(String portal) {
        setVariableValue(PORTAL, quoteString(portal));
    }

    @Override
    public void parseOutput() {
        String stdout = getOutput().getStdout();
        if (StringUtils.isNotBlank(stdout)) {
            results = new DiscoverIScsiTargetsParser().parseTargets(stdout);
        }
        else {
            results = Lists.newArrayList();
        }
    }
}
