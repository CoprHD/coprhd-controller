/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.exceptions.TimeoutException;

/**
 * Wrapper around a ViPR Task that allows for monitoring progress and updating the execution logs on success/failure.
 *
 * @param <T>
 *        the result type of the ViPR task.
 */
public class ViPRTaskMonitor<T> {
    private final ExecutionContext context;
    private final ExecutionTaskLog log;
    private final Task<T> task;

    /** Whether the task has been completed. */
    private boolean complete;
    /** The result of the task. */
    private T value;
    /** The error if the task failed. */
    private ExecutionException error;

    public ViPRTaskMonitor(ExecutionContext context, ExecutionTaskLog log, Task<T> task) {
        this.context = context;
        this.log = log;
        this.task = task;
    }

    /**
     * Waits until the task completes. This is equivalent to {@code waitFor(-1)}.
     */
    public void waitFor() {
        waitFor(-1);
    }

    /**
     * Waits until the task completes or the specified timeout (millis), whichever is first.
     * 
     * @param timeout
     *        the maximum time to wait.
     * @return whether the task completed or not.
     */
    public boolean waitFor(long timeout) {
        if (!complete) {
            try {
                value = task.get(timeout);
                complete = true;
                context.updateTaskLog(log, elapsedTime());
            }
            catch (TimeoutException e) {
                // Ignore
            }
            catch (Exception e) {
                error = new ExecutionException(e);
                complete = true;
                context.updateTaskLog(log, elapsedTime(), e);
            }
        }
        return complete;
    }

    /**
     * Checks the task for complete. If the task has already completed this will return right away, otherwise the task
     * is refreshed and checked for complete.
     * 
     * @return whether the task is completed.
     */
    public boolean check() {
        if (!complete) {
            try {
                task.refresh();
                complete = task.isComplete();
                if (complete) {
                    value = task.get();
                }
                context.updateTaskLog(log, elapsedTime());
            }
            catch (TimeoutException e) {
                // Ignore
            }
            catch (Exception e) {
                error = new ExecutionException(e);
                complete = true;
                context.updateTaskLog(log, elapsedTime(), e);
            }
        }
        return complete;
    }

    /**
     * Determines if the task is complete (success or failure).
     * 
     * @return whether the task is complete.
     */
    public boolean isComplete() {
        return complete;
    }

    /**
     * Determines if the task completed successfully.
     * 
     * @return whether the task completed successfully.
     */
    public boolean isSuccess() {
        return complete && (error == null);
    }

    /**
     * Determines if the task completed with an error.
     * 
     * @return whether the task completed with an error.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Gets the underlying ViPR task.
     * 
     * @return the task.
     */
    public Task<T> getTask() {
        return task;
    }

    /**
     * Gets the error wrapping the thrown error (if the task completed in error).
     * 
     * @return the error.
     */
    public ExecutionException getError() {
        return error;
    }

    /**
     * Waits for the task to complete and returns the result of the task. If the task fails the error is thrown.
     * 
     * @return the value
     * 
     * @throws ExecutionException
     *         if the task failed.
     */
    public T getValue() {
        waitFor();
        if (error != null) {
            throw error;
        }
        return value;
    }

    /**
     * Gets the elapsed time of the task, or the elapsed time to now if the task has not yet completed.
     * 
     * @return the elapsed time of the task.
     */
    private long elapsedTime() {
        long startTime = log.getDate().getTime();
        if (task.getEndTime() != null) {
            return task.getEndTime().getTimeInMillis() - startTime;
        }
        else {
            return System.currentTimeMillis() - startTime;
        }
    }
}
