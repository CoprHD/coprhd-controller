/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.fdisk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;

public class GetFilesystemBlockSizeCommand extends LinuxResultsCommand<String> {
    private static final Pattern diskPattern = Pattern.compile("\\w+\\s+[0-9]+\\s+[0-9]+\\s+([0-9]+)");

    public GetFilesystemBlockSizeCommand(String path) {
        setCommand(CommandConstants.FDISK);
        addArguments("-l");
        addArguments(path);
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        Matcher matcher = diskPattern.matcher(getOutput().getStdout());
        System.out.println(getOutput().getStdout());
        while (matcher.find()) {
            results = matcher.group(1);
        }
    }
}
