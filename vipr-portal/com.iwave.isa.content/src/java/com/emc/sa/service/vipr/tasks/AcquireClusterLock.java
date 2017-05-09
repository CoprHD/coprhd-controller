/*
 * Copyright (c) 2016-2017
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.db.client.model.Cluster;

public class AcquireClusterLock extends ExecutionTask<Void> {
    private Cluster cluster;

    public AcquireClusterLock(Cluster cluster) {
        this.cluster = cluster;
        provideDetailArgs(cluster.getLabel(), cluster.getId());
    }

    @Override
    public void execute() throws Exception {
        acquireClusterLock();
    }

    private void acquireClusterLock() {
        if (cluster != null) {
            String lockName = cluster.getId().toString();
            if (!ExecutionUtils.acquireLock(lockName)) {
                throw stateException("AcquireHostLock.illegalState.failedClusterLock", lockName);
            }
        }
    }
}
