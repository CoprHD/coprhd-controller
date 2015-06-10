/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

public class AddDiskToCluster extends WindowsExecutionTask<String> {
    private final String diskId;

    public  AddDiskToCluster(String diskId) {
        this.diskId = diskId;
        provideDetailArgs(diskId);
    }

    @Override
    public String executeTask() throws Exception {
        return getTargetSystem().addDiskToCluster(diskId);
    }
}
