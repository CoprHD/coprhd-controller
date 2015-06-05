/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.vipr.client.Task;

public abstract class LongRunningTask<T> extends ViPRExecutionTask<Task<T>> {
    private boolean waitFor;
    private long timeout = -1;

    public boolean isWaitFor() {
        return waitFor;
    }

    public void setWaitFor(boolean waitFor) {
        this.waitFor = waitFor;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    @Override
    public final Task<T> executeTask() throws Exception {
        Task<T> task = doExecute();
        addOrderIdTag(task.getTaskResource().getId());
        if (waitFor) {
            info("Waiting for task to complete: %s on resource: %s", task.getOpId(), task.getResourceId());
            task.waitFor(timeout);
        }
        return task;
    }

    protected abstract Task<T> doExecute() throws Exception;
}
