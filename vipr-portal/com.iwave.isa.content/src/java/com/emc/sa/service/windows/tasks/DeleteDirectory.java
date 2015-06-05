/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

public class DeleteDirectory extends WindowsExecutionTask<Void> {

    private String directory;
    
    public DeleteDirectory(String directory) {
        this.directory = directory;
        provideDetailArgs(directory);
    }
    
    @Override
    public void execute() throws Exception {
        getTargetSystem().deleteDirectory(directory);
    }
    
}
