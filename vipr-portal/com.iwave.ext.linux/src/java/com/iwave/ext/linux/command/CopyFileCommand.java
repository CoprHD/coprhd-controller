/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Copys a file to the external host
 */
public class CopyFileCommand extends LinuxResultsCommand<String> {

    public CopyFileCommand(String srcFile, String tgtFile) 
            throws FileNotFoundException {
        StringBuilder command = new StringBuilder("echo");
        String content = new Scanner(new File(srcFile)).useDelimiter("\\Z").next();
        command.append(" ").append(content).append(" > ").append(tgtFile);
        setCommand(command.toString());
    }

    @Override
    public void parseOutput() {
        results = getOutput().getStdout();
    }

}
