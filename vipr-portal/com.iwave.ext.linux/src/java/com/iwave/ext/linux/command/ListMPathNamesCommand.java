/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.linux.command.LinuxResultsCommand;

/**
 * Lists the names of the mpaths on the system.
 * 
 * @author jonnymiller
 */
public class ListMPathNamesCommand extends LinuxResultsCommand<List<String>> {

    public ListMPathNamesCommand() {
        setCommand(CommandConstants.MULTIPATH);
        addArguments("-v1", "-ll");
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();
        String[] values = getOutput().getStdout().split("\\n");
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                results.add(StringUtils.trim(value));
            }
        }
        Collections.sort(results);
    }
}
