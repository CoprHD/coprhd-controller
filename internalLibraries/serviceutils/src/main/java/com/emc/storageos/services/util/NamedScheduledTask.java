/*
 * Copyright (c) 2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.services.util;

import java.util.concurrent.Delayed;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of RunnableScheduledFuture which supports a name
 * 
 */
class NamedScheduledTask<V> extends NamedTask<V> implements RunnableScheduledFuture<V> {

    public NamedScheduledTask(String name, RunnableScheduledFuture<V> future) {
        super(name, future);
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return getRunnableScheduledFuture().getDelay(unit);
    }

    @Override
    public int compareTo(Delayed o) {
        return getRunnableScheduledFuture().compareTo(o);
    }

    @Override
    public boolean isPeriodic() {
        return getRunnableScheduledFuture().isPeriodic();
    }

    private RunnableScheduledFuture<V> getRunnableScheduledFuture() {
        return (RunnableScheduledFuture<V>) super.future;
    }
}
