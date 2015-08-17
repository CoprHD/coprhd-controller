/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.iwave.ext.windows.WindowsUtils;

public class RescanDisks extends DiskPartScript<Void> {
    public RescanDisks() {
        setDiskPartCommands(WindowsUtils.getRescanCommands());
    }

    @Override
    public void execute() throws Exception {
        String output = getTargetSystem().rescanDisks();
        logDebug(output);
    }
}
