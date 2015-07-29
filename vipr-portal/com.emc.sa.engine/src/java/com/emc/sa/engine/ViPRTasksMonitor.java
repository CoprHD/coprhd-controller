/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.engine;

import java.util.List;

import com.emc.storageos.db.client.model.uimodels.ExecutionTaskLog;
import com.emc.vipr.client.Task;
import com.emc.vipr.client.Tasks;
import com.emc.vipr.client.exceptions.TimeoutException;

/**
 * Wrapper around a ViPR Tasks that allow for monitoring progress and updating the execution log on success/failure.
 * 
 * @param <T>
 *            the result type of the ViPR Tasks
 */
public class ViPRTasksMonitor<T> {
    private final ExecutionContext context;
    private final ExecutionTaskLog log;
    private final Tasks<T> tasks;

    /** Whether all tasks have completed. */
    private boolean complete;
    /** The results of the tasks. */
    private List<T> values;
    /** The error if the tasks failed. */
    private ExecutionException error;

    public ViPRTasksMonitor(ExecutionContext context, ExecutionTaskLog log, Tasks<T> tasks) {
        this.context = context;
        this.log = log;
        this.tasks = tasks;
    }

    /**
     * Waits until the tasks complete. This is equivalent to {@code waitFor(-1)}.
     */
    public void waitFor() {
        waitFor(-1);
    }

    /**
     * Waits until the tasks complete or the specified timeout (millis), whichever is first.
     * 
     * @param timeout
     *            the maximum time to wait.
     * @return whether the tasks completed or not.
     */
    public boolean waitFor(long timeout) {
        if (!complete) {
            try {
                values = tasks.get(timeout);
                complete = true;
                context.updateTaskLog(log, elapsedTime());
            } catch (TimeoutException e) {
                // Ignore
            } catch (Exception e) {
                error = new ExecutionException(e);
                complete = true;
                context.updateTaskLog(log, elapsedTime(), e);
            }
        }
        return complete;
    }

    /**
     * Checks the tasks for complete. If all tasks have already completed this will return right away, otherwise the
     * tasks are refreshed and checked for complete.
     * 
     * @return whether the tasks are completed.
     */
    public boolean check() {
        if (!complete) {
            try {
                refreshTasks();
                complete = isTasksComplete();
                if (complete) {
                    values = tasks.get();
                }
                context.updateTaskLog(log, elapsedTime());
            } catch (TimeoutException e) {
                // Ignore
            } catch (Exception e) {
                error = new ExecutionException(e);
                complete = true;
                context.updateTaskLog(log, elapsedTime(), e);
            }
        }
        return complete;
    }

    /**
     * Refreshes all tasks.
     */
    private void refreshTasks() {
        for (Task<T> task : tasks.getTasks()) {
            task.refresh();
        }
    }

    /**
     * Whether all tasks are complete.
     * 
     * @return true if all tasks are complete.
     */
    private boolean isTasksComplete() {
        for (Task<T> task : tasks.getTasks()) {
            if (!task.isComplete()) {
                return false;
            }
        }
        return true;
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
     * Determines if the tasks completed successfully.
     * 
     * @return whether the tasks completed successfully.
     */
    public boolean isSuccess() {
        return complete && (error == null);
    }

    /**
     * Determines if the tasks completed with an error.
     * 
     * @return whether the tasks completed with an error.
     */
    public boolean isError() {
        return error != null;
    }

    /**
     * Gets the underlying ViPR tasks.
     * 
     * @return the tasks.
     */
    public Tasks<T> getTasks() {
        return tasks;
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
     * Waits for the tasks to complete and returns the results of the tasks. If the tasks failed the error is thrown.
     * 
     * @return the results of the tasks.
     * 
     * @throws ExecutionException
     *             if the tasks completed with an error.
     */
    public List<T> getValues() {
        waitFor();
        if (error != null) {
            throw error;
        }
        return values;
    }

    /**
     * Gets the elapsed time of the tasks.
     * 
     * @return the elapsed time.
     */
    public long elapsedTime() {
        return endTime() - startTime();
    }

    /**
     * Gets the start time of the first task.
     * 
     * @return the start time of the first task.
     */
    private long startTime() {
        long start = log.getDate().getTime();
        for (Task<?> task : tasks.getTasks()) {
            if (task.getStartTime() != null) {
                start = Math.min(start, task.getStartTime().getTimeInMillis());
            }
        }
        return start;
    }

    /**
     * Gets the end time of the latest finished task or the current time if no tasks are yet finished.
     * 
     * @return the end time of the latest finished task.
     */
    private long endTime() {
        long endTime = System.currentTimeMillis();
        Task<T> finishedTask = tasks.latestFinishedTask();
        if (finishedTask != null && finishedTask.getEndTime() != null) {
            endTime = finishedTask.getEndTime().getTimeInMillis();
        }
        return endTime;
    }
}
