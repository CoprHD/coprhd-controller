/*
 * Copyright (c) 2017 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;

public class LsOnMountPointCommand extends LinuxResultsCommand<List<String>> {

    public LsOnMountPointCommand(String mountPoint) {
        StringBuffer commandBuffer = new StringBuffer();
        commandBuffer.append("cd ");
        commandBuffer.append(mountPoint);
        commandBuffer.append(";ls");

        setCommand(commandBuffer.toString());
    }

    @SuppressWarnings("unused")
    private LsOnMountPointCommand() {

    }

    @Override
    public void parseOutput() {

        results = Lists.newArrayList();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String[] lines = getOutput().getStdout().split("\n");
            for (String line : lines) {
                String s = StringUtils.substringBefore(line, "#");
                if (StringUtils.isNotBlank(s)) {

                    if (s.contains("No such file or directory")) {
                        break;
                    } else if (!s.equals("lost+found")) {
                        results.add(s);
                    }
                }
            }
        }
    }
}
