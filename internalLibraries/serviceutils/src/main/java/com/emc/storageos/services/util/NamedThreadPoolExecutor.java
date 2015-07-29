/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

package com.emc.storageos.services.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Class to create unified ThreadPoolExecutor so that threads are created with
 * pool names embedded.
 * 
 * @author luoq1
 */
public class NamedThreadPoolExecutor extends ThreadPoolExecutor {

    // if this is set, the running task name will be append to the executing
    // thread name.
    private boolean appendTaskName = false;
    private String poolName;

    /**
     * Creates a executor with fixed pool size and a prefix for thread name.
     * 
     * @param poolName
     *            - the name prefix for any threads in the pool
     * @param fixedPoolSize
     *            - the number of threads to keep in the pool.
     */
    public NamedThreadPoolExecutor(String poolName, int fixedPoolSize) {
        super(fixedPoolSize, fixedPoolSize, 0L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new NamedThreadFactory(poolName));
        setPoolName(poolName);
    }

    /**
     * Creates a new ThreadPoolExecutor with the given parameters.
     * 
     * @param poolName
     *            - the name prefix for any threads in the pool. It is used by
     *            the attached ThreadFatory.
     * @param corePoolSize
     *            - the number of threads to keep in the pool, even if they are
     *            idle, unless allowCoreThreadTimeOut is set.
     * @param maximumPoolSize
     *            - the maximum number of threads to allow in the pool
     * @param keepAliveTime
     *            - when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param unit
     *            - the time unit for the keepAliveTime argument
     * @param queue
     *            - the queue to use for holding tasks before they are executed.
     *            This queue will hold only the Runnable tasks submitted by the
     *            execute method.
     */
    public NamedThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> queue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, new NamedThreadFactory(
                poolName));
        setPoolName(poolName);
    }

    /**
     * Creates a new ThreadPoolExecutor with the given parameters.
     * 
     * @param poolName
     *            - the name prefix for any threads in the pool. It is used by
     *            the attached ThreadFatory.
     * @param corePoolSize
     *            - the number of threads to keep in the pool, even if they are
     *            idle, unless allowCoreThreadTimeOut is set.
     * @param maximumPoolSize
     *            - the maximum number of threads to allow in the pool
     * @param keepAliveTime
     *            - when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param unit
     *            - the time unit for the keepAliveTime argument
     * @param queue
     *            - the queue to use for holding tasks before they are executed.
     *            This queue will hold only the Runnable tasks submitted by the
     *            execute method.
     * @param handler
     *            - the handler to use when execution is blocked because the
     *            thread bounds and queue capacities are reached
     */
    public NamedThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> queue,
            RejectedExecutionHandler handler) {
        super(corePoolSize, corePoolSize, keepAliveTime, unit, queue, new NamedThreadFactory(
                poolName), handler);
        setPoolName(poolName);
    }

    /**
     * Creates a new ThreadPoolExecutor with the given parameters.
     * 
     * @param poolName
     *            - the name prefix for any threads in the pool. It is used by
     *            the attached ThreadFatory.
     * @param corePoolSize
     *            - the number of threads to keep in the pool, even if they are
     *            idle, unless allowCoreThreadTimeOut is set.
     * @param maximumPoolSize
     *            - the maximum number of threads to allow in the pool
     * @param keepAliveTime
     *            - when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param unit
     *            - the time unit for the keepAliveTime argument
     * @param queue
     *            - the queue to use for holding tasks before they are executed.
     *            This queue will hold only the Runnable tasks submitted by the
     *            execute method.
     * @param factory
     *            - the ThreadFactory user provided. It is wrapped into
     *            NamedThreadFactory to keep the thread name format - see@NamedThreadFactory
     */
    public NamedThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> queue, ThreadFactory factory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, new NamedThreadFactory(
                poolName, factory));
        setPoolName(poolName);
    }

    /**
     * Creates a new ThreadPoolExecutor with the given parameters.
     * 
     * @param poolName
     *            - the name prefix for any threads in the pool. It is used by
     *            the attached ThreadFatory.
     * @param corePoolSize
     *            - the number of threads to keep in the pool, even if they are
     *            idle, unless allowCoreThreadTimeOut is set.
     * @param maximumPoolSize
     *            - the maximum number of threads to allow in the pool
     * @param keepAliveTime
     *            - when the number of threads is greater than the core, this is
     *            the maximum time that excess idle threads will wait for new
     *            tasks before terminating.
     * @param unit
     *            - the time unit for the keepAliveTime argument
     * @param queue
     *            - the queue to use for holding tasks before they are executed.
     *            This queue will hold only the Runnable tasks submitted by the
     *            execute method.
     * @param factory
     *            - the ThreadFactory user provided. It is wrapped into
     *            NamedThreadFactory to keep the thread name format - see@NamedThreadFactory
     * @param handler
     *            - the handler to use when execution is blocked because the
     *            thread bounds and queue capacities are reached
     */
    public NamedThreadPoolExecutor(String poolName, int corePoolSize, int maximumPoolSize,
            long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> queue,
            ThreadFactory factory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, queue, new NamedThreadFactory(
                poolName, factory));
        setPoolName(poolName);
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

    @Override
    protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
        return new NamedTask<V>(c.getClass().getSimpleName(), c);
    }

    @Override
    protected <V> RunnableFuture<V> newTaskFor(Runnable r, V v) {
        return new NamedTask<V>(r.getClass().getSimpleName(), r, v);
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

    public String getPoolName() {
        return poolName;
    }

    private void setPoolName(String poolName) {
        this.poolName = poolName;
    }

}