/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.aix.tasks;

import com.iwave.ext.linux.command.MkdirCommand;

public class CreateDirectory extends AixExecutionTask<Void> {
    
    private String path;
    
    public CreateDirectory(String path) {
        this.path = path;
    }
    
    @Override
    public void execute() throws Exception {
        MkdirCommand command = new MkdirCommand(true);
        command.setDir(path);
        executeCommand(command, SHORT_TIMEOUT);
    }
}