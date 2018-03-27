/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.hpux.command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HP-UX command for getting the block size of the filesystem device
 * This command runs the bdf device command and gets the block size of the partition
 * 
 */
public class GetFilesystemBlockSizeCommand extends HpuxResultsCommand<String> {

    private static final Pattern partitionPattern = Pattern.compile("\\/\\w+\\s+([0-9]+)");

    public GetFilesystemBlockSizeCommand(String device) {
        setCommand(String.format("bdf %s", device));
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