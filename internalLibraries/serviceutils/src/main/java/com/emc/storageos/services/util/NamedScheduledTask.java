/**
* Copyright 2015 EMC Corporation
* All Rights Reserved
 */
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
