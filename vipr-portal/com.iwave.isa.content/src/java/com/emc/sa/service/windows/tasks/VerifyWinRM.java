/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;


public class VerifyWinRM extends WindowsExecutionTask<Void> {
    
    public VerifyWinRM() {
    }

    @Override
    public void execute() throws Exception {
        String url = getTargetSystem().getTarget().getUrl().toExternalForm();
        provideDetailArgs(url);
        getTargetSystem().getRegistryKeys("HARDWARE\\DEVICEMAP\\Scsi");
    }
    
}
