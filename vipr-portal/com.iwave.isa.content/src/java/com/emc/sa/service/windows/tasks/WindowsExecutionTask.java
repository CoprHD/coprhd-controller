/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.windows.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.windows.WindowsSystemWinRM;

public class WindowsExecutionTask<T> extends ExecutionTask<T> {
    private WindowsSystemWinRM targetSystem;

    public WindowsSystemWinRM getTargetSystem() {
        return targetSystem;
    }

    public void setTargetSystem(WindowsSystemWinRM targetSystem) {
        this.targetSystem = targetSystem;
    }
}
