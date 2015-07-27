/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import com.google.common.collect.Lists;
import com.iwave.ext.command.CommandOutput;


public class GetDirectoryContents extends WindowsExecutionTask<List<String>> {

    private String directory;
    
    public GetDirectoryContents(String directory) {
        this.directory = directory;
        provideDetailArgs(directory);
    }
    
    @Override
    public List<String> executeTask() throws Exception {
        CommandOutput output = getTargetSystem().getDirectoryContents(directory);
        
        if (StringUtils.isBlank(output.getStdout())) {
            return Collections.emptyList();
        }
        else {
            return Lists.newArrayList(output.getStdout().split("\n"));
        }
    }
    
}
