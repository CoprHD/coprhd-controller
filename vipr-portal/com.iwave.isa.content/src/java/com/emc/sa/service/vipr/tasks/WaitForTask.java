/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

public abstract class WaitForTask<T> extends LongRunningTask<T> {
    public WaitForTask() {
        setWaitFor(true);
    }
}
