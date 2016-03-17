/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;

/**
 * Customized ScheduledThreadPoolExecutor that provides: - Name prefix for all
 * threads in the pool via @NamedThreadFactory - A Embedded @NamedThreadFactory
 * that creates threads with prefix
 * 
 * @author luoq1
 * 
 */
public class NamedScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {
    // append running task name to the executing thread name?
    private boolean appendTaskName = false;
    // the name prefix for the threads in the pool
    private String poolName;

    /**
     * Creates a ScheduledThreadPoolExecutor with fixed poolName size and a
     * prefix for thread name.
     * 
     * @param poolName
     *            - the name prefix for any threads in the poolName
     * @param fixedPoolSize
     *            - the number of threads to keep in the pool.
     */
    public NamedScheduledThreadPoolExecutor(String poolName, int fixPoolSize) {
        super(fixPoolSize, new NamedThreadFactory(poolName));
        setPoolName(poolName);
    }

    /**
     * Creates a ScheduledThreadPoolExecutor with fixed poolName size and a
     * prefix for thread name.
     * 
     * @param poolName
     *            - the name prefix for any threads in the poolName
     * @param fixPoolSize
     *            - the number of threads to keep in the pool.
     * @param handler
     *            - the handler to use when execution is blocked because the
     *            thread bounds and queue capacities are reached
     */
    public NamedScheduledThreadPoolExecutor(String poolName, int fixPoolSize,
            RejectedExecutionHandler handler) {
        super(fixPoolSize, new NamedThreadFactory(poolName), handler);
        setPoolName(poolName);
    }

    /**
     * Creates a ScheduledThreadPoolExecutor with fixed poolName size and a
     * prefix for thread name.
     * 
     * @param poolName
     *            - the name prefix for any threads in the poolName
     * @param fixPoolSize
     *            - the number of threads to keep in the pool.
     * @param threadFactory
     *            - thread factory user gave will be wrapped into a @NamedThreadFactory
     */
    public NamedScheduledThreadPoolExecutor(String poolName, int fixPoolSize,
            ThreadFactory threadFactory) {
        super(fixPoolSize, new NamedThreadFactory(poolName, threadFactory));
        setPoolName(poolName);
    }

    /**
     * Creates a ScheduledThreadPoolExecutor with fixed poolName size and a
     * prefix for thread name.
     * 
     * @param poolName
     *            - the name prefix for any threads in the poolName
     * @param fixPoolSize
     *            - the number of threads to keep in the pool.
     * @param threadFactory
     *            - thread factory user gave will be wrapped into a @NamedThreadFactory
     * @param handler
     *            - the handler to use when execution is blocked because the
     *            thread bounds and queue capacities are reached
     */
    public NamedScheduledThreadPoolExecutor(String poolName, int fixPoolSize,
            ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(fixPoolSize, new NamedThreadFactory(poolName, threadFactory), handler);
        setPoolName(poolName);
    }

    // ------Methods ----------------------------------
    // -------------------------------------------------
    // beforeExecute() Append running task name
    // afterExecute() Reset thead name after task
    // -------------------------------------------------
    /**
     * Wrap the running task within @NamedTask so that we can easily capture
     * its name. The default decorateTask method from super class @ScheduledThreadPoolExecutor
     * hides every runnable within its own wrapped class -
     * 
     * @RunnableScheduledFuture.
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Runnable r,
            RunnableScheduledFuture<V> task) {
        return new NamedScheduledTask<V>(r.getClass().getSimpleName(), task);
    }

    /**
     * See comments for above method.
     */
    @Override
    protected <V> RunnableScheduledFuture<V> decorateTask(Callable<V> c,
            RunnableScheduledFuture<V> task) {
        return new NamedScheduledTask<V>(c.getClass().getSimpleName(), task);
    }

    /**
     * Changes executing thread name to append the task name if appendTaskName
     * is set to true.
     */
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        if (isAppendTaskName()) {
            NamedThreadPoolHelper.changeNameBeforeExecute(t, r);
        }
        super.beforeExecute(t, r);
    }

    /**
     * Resets the thread name back to default thread pool name(prefix).
     */
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        if (isAppendTaskName()) {
            NamedThreadPoolHelper.resetNameAfterExecute(r, t);
        }
        super.afterExecute(r, t);
    }

    /**
     * Checks if the appendTaskName flag is set.
     * 
     * @return - true/false
     */
    public boolean isAppendTaskName() {
        return appendTaskName;
    }

    /**
     * Sets the appendTaskName flag, true to append the task name to running
     * thread's name.
     * 
     * @param appendTaskName
     */
    public void setAppendTaskName(boolean appendTaskName) {
        this.appendTaskName = appendTaskName;
    }

    /**
     * Finds the prefix name.
     * 
     * @return
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Sets the prefix name.
     * 
     * @param poolName
     */
    private void setPoolName(String poolName) {
        this.poolName = poolName;
    }
}
