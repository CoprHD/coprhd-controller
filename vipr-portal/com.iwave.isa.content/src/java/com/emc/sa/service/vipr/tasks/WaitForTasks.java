/*
 * Copyright 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

public abstract class WaitForTasks<T> extends LongRunningTasks<T> {
    public WaitForTasks() {
        setWaitFor(true);
    }
}
