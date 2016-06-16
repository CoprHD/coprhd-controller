/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.StringUtils;

/**
 * Copys a file to the external host
 */
public class CopyFileCommand extends LinuxResultsCommand<String> {

    public CopyFileCommand(String content, String tgtFile) {
        StringBuilder command = new StringBuilder("echo");
        command.append(" ").append(content).append(" > ").append(tgtFile);
        setCommand(command.toString());
    }

    @Override
    public void parseOutput() {
        results = getOutput().getStdout();
    }

}
