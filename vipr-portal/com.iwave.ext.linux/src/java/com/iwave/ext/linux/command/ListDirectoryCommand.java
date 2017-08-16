/*
 * Copyright (c) 2017 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;

public class ListDirectoryCommand extends LinuxResultsCommand<List<String>> {

    public ListDirectoryCommand(String dir) {
        setCommand("ls");
        addArgument(dir);
    }

    @Override
    public void parseOutput() {
        results = Lists.newArrayList();
        // Get all file and folders in given directory!!!
        if (getOutput() != null && getOutput().getStdout() != null) {
            String[] lines = getOutput().getStdout().split("\n");
            for (String line : lines) {
                if (StringUtils.isNotBlank(line)) {
                    String[] files = StringUtils.trim(line).split("\\s+");
                    results.addAll(Arrays.asList(files));
                }
            }
        }
    }

}
