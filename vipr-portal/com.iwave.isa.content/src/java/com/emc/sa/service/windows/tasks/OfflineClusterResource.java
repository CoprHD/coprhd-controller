/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

/**
 */
public class OfflineClusterResource extends WindowsExecutionTask<Void> {
    private final String resourceName;

    public OfflineClusterResource(String resourceName) {
        this.resourceName = resourceName;
        provideDetailArgs(resourceName);
    }

    @Override
    public void execute() throws Exception {
        getTargetSystem().offlineClusterResource(resourceName);
    }
}
