/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.iwave.ext.linux.command;

import org.apache.commons.lang.StringUtils;

/**
 * get the device name for the first partition on the device supplied.
 */
public class CatCommand extends LinuxResultsCommand<String> {
    
    public CatCommand(String path) {
        this(path, null);
    }
    
    public CatCommand(String path, String grepString) {
        StringBuilder command = new StringBuilder("cat");
        command.append(" ").append(path);
        if (!StringUtils.isBlank(grepString)) {
            command.append(" | grep ").append(grepString);
        }
        setCommand(command.toString());
    }

    @Override
    public void parseOutput() {
        results = getOutput().getStdout();        
    }
    
}
