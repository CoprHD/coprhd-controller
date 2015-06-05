/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
package com.emc.storageos.services.util;

/**
 * Copyright (c) 2013. EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Implementation of RunnableFuture which supports a name
 * 
 */
class NamedTask<V> implements RunnableFuture<V> {

    protected final RunnableFuture<V> future;
    protected final String name;

    protected NamedTask(String name, RunnableFuture<V> future) {
        this.future = future;
        this.name = name;
    }

    public NamedTask(String name, Callable<V> c) {
        this.name = name;
        this.future = new FutureTask<V>(c);
    }

    public NamedTask(String name, Runnable r, V v) {
        this.name = name;
        this.future = new FutureTask<V>(r, v);
    }

    public String getName() {
        return this.name;
    }

    @Override
    public void run() {
        future.run();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return future.cancel(mayInterruptIfRunning);
    }

    @Override
    public V get() throws InterruptedException, ExecutionException {
        return future.get();
    }

    @Override
    public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException,
            TimeoutException {
        return future.get(timeout, unit);
    }

    @Override
    public boolean isCancelled() {
        return future.isCancelled();
    }

    @Override
    public boolean isDone() {
        return future.isDone();
    }
}
