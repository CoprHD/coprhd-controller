/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

/**
 */
public class DeleteClusterResource extends WindowsExecutionTask<Void> {
    private final String resourceName;

    public DeleteClusterResource(String resourceName) {
        this.resourceName = resourceName;
        provideDetailArgs(resourceName);
    }

    @Override
    public void execute() throws Exception {
        getTargetSystem().deleteClusterResource(resourceName);
    }
}
