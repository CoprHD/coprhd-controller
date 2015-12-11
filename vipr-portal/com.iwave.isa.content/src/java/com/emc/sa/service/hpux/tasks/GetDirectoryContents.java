/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandOutput;

/**
 */
public class GetDirectoryContents extends HpuxExecutionTask<List<String>> {
    private String command;

    public GetDirectoryContents(String directory) {
        this.command = "ls " + directory;
        setName("GetDirectoryContents.name");
        setDetail(command);
    }

    @Override
    public List<String> executeTask() throws Exception {
        CommandOutput output = getTargetCLI().executeCommand(command);

        if (StringUtils.isBlank(output.getStdout())) {
            return Collections.emptyList();
        }
        else {
            return Lists.newArrayList(output.getStdout().split("\n"));
        }
    }
}
