/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command.fdisk;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.iwave.ext.linux.command.CommandConstants;
import com.iwave.ext.linux.command.LinuxResultsCommand;

/**
 * Linux command for getting the block size of the filesystem device
 * This command runs the fdisk -l device command and gets the block size of the partition
 * 
 */
public class GetFilesystemBlockSizeCommand extends LinuxResultsCommand<String> {

    private static final Pattern partitionPattern = Pattern.compile("\\w+\\s+[0-9]+\\s+[0-9]+\\s+([0-9]+)");
    private static final String LIST_OPTION = "-l";

    public GetFilesystemBlockSizeCommand(String device) {
        setCommand(CommandConstants.FDISK);
        addArguments(LIST_OPTION);
        addArguments(device);
        setRunAsRoot(true);
    }

    @Override
    public void parseOutput() {
        Matcher matcher = partitionPattern.matcher(getOutput().getStdout());
        while (matcher.find()) {
            results = matcher.group(1);
        }
    }
}
