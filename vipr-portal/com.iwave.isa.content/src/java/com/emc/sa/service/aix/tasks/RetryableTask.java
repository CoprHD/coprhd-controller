/*
 * Copyright (c) 2015 EMC
 */
package com.emc.sa.service.aix.tasks;

import org.apache.commons.lang.StringUtils;

import com.emc.sa.service.vmware.VMwareUtils;
import com.iwave.ext.command.CommandException;
import com.vmware.vim25.MethodFault;

public abstract class RetryableTask<T> extends AixExecutionTask<T> {

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
        } catch (CommandException e) {
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

    protected abstract boolean canRetry(CommandException e);

    protected void fail(CommandException e) {
        String message = getMessage(e);
        logError("retryable.task.failed.with.error", getName(), message);
        throw e;
    }

    protected String getMessage(CommandException e) {
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
