/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import java.util.Map;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Maps;

public class ListManualMountsCommand extends LinuxResultsCommand<Map<String, String>> {

    public ListManualMountsCommand(String mountPoint) {
        setCommand("df -h");
    }

    @SuppressWarnings("unused")
    private ListManualMountsCommand() {

    }

    @Override
    public void parseOutput() {
        results = Maps.newHashMap();

        if (getOutput() != null && getOutput().getStdout() != null) {
            String[] lines = getOutput().getStdout().split("\n");
            for (String line : lines) {
                String s = StringUtils.substringBefore(line, "#");
                if (StringUtils.isNotBlank(s)) {
                    String[] pieces = StringUtils.trim(s).split("\\s+");

                    String fsPath = pieces[0];
                    String mountPath = "";
                    if (pieces.length > 5) {
                        mountPath = pieces[5];
                    }
                    if (StringUtils.isNotBlank(fsPath) && StringUtils.isNotBlank(mountPath)) {
                        results.put(fsPath, mountPath);
                    }
                }
            }
        }
    }
}
