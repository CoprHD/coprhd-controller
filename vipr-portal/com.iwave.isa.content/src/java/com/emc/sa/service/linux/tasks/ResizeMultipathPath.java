/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.linux.tasks;

import com.iwave.ext.linux.command.MultipathdResizeCommand;


public class ResizeMultipathPath extends LinuxExecutionTask<Void> {

    private String device;
    
    public ResizeMultipathPath(String device) {
        this.device = device;
    }
    
    @Override
    public void execute() throws Exception {
        executeCommand(new MultipathdResizeCommand(device));
    }

}
