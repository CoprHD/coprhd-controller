/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.hpux.tasks;

import org.apache.commons.lang.StringUtils;

import com.iwave.ext.command.CommandException;

/**
 * The RetryableCommandTask retries an HpuxExecutionTask up to a default of five times. Subclasses are responsible
 * for implementing canRetry for thrown CommandExceptions
 * 
 * @author Jay Logelin
 *
 * @param <T> the result Type
 * @param <E> the Exception Type with which to check retry capabilities
 */
public abstract class RetryableCommandTask<T, E extends CommandException> extends HpuxExecutionTask<T> {

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
            success = tryOnce(tries);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private boolean tryOnce(int tries) {
        try {
            result = tryExecute();
            return true;
        } catch (CommandException e) {
            if (!canRetry((E) e)) {
                fail((E) e);
            }
            else if (tries >= maxTries) {
                logError("retryable.task.max.retries", getName());
                fail((E) e);
            }
            String message = getMessage((E) e);
            logInfo("retryable.task.failed.retry", getName(), message);
            return false;
        }
    }

    protected abstract T tryExecute();

    protected abstract boolean canRetry(E e);

    protected void fail(E e) {
        String message = getMessage(e);
        logError("retryable.task.failed.with.error", getName(), message);
        throw e;
    }

    protected String getMessage(E e) {
        String message = null;
        String defaultMessage = null;
        Throwable cause = e.getCause();
        if (cause != null) {
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
