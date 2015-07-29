/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

public class MakeDirectory extends WindowsExecutionTask<Void> {

    private final String directory;

    public MakeDirectory(String directory) {
        this.directory = directory;
        provideDetailArgs(directory);
    }

    @Override
    public void execute() throws Exception {
        getTargetSystem().makeDirectory(directory);
    }

}
