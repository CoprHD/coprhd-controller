/*
 * Copyright (c) 2012-2015 iWave Software LLC
 * All Rights Reserved
 */
package com.emc.sa.service.vmware.tasks;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.engine.ExecutionTask;
import com.iwave.ext.vmware.VMWareException;
import com.iwave.ext.vmware.VMwareUtils;
import com.vmware.vim25.MethodFault;

/**
 * Base task for retryable
 * 
 * @author jonnymiller
 * 
 * @param <T>
 */
public abstract class RetryableTask<T> extends ExecutionTask<T> {
    private static final int DEFAULT_MAX_TRIES = 5;
    private static final long DEFAULT_DELAY = 10000;
    private int maxTries = DEFAULT_MAX_TRIES;
    private long delay = DEFAULT_DELAY;
    private T result;

    public int getMaxTries() {
        return maxTries;
    }

    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    public long getDelay() {
        return delay;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    @Override
    public T executeTask() throws Exception {
        debug("Executing: %s", getDetail());

        int tries = 1;
        boolean success = tryOnce(tries);
        while (!success) {
            tries++;
            Thread.sleep(delay);
            logInfo("retryable.task", getName());
            beforeRetry();
            success = tryOnce(tries);
        }
        return result;
    }

    private boolean tryOnce(int tries) {
        try {
            result = tryExecute();
            return true;
        } catch (VMWareException e) {
            if (!canRetry(e)) {
                fail(e);
            }
            else if (tries >= maxTries) {
                logError("retryable.task.max.retries", getName());
                fail(e);
            }
            String message = getMessage(e);
            logInfo("retryable.task.failed.retry", getName(), message);
            return false;
        }
    }

    protected void beforeRetry() {
    }

    protected abstract T tryExecute();

    protected abstract boolean canRetry(VMWareException e);

    protected void fail(VMWareException e) {
        String message = getMessage(e);
        logError("retryable.task.failed.with.error", getName(), message);
        throw e;
    }

    protected String getMessage(VMWareException e) {
        String message = null;
        String defaultMessage = null;
        Throwable cause = e.getCause();
        if (cause instanceof MethodFault) {
            message = VMwareUtils.getFaultMessage((MethodFault) cause);
            defaultMessage = cause.getClass().getName();
        }
        else if (cause != null) {
            message = cause.getMessage();
            defaultMessage = cause.getClass().getName();
        }
        else {
            message = e.getMessage();
            defaultMessage = e.getClass().getName();
        }
        return StringUtils.defaultIfBlank(message, defaultMessage);
    }

}
