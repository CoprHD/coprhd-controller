/**
* Copyright 2012-2015 iWave Software LLC
* All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;

public class AcquireHostLock extends ExecutionTask<Void> {
    private Host host;
    private Cluster cluster;

    public AcquireHostLock(Host host) {
        this(host, null);
    }

    public AcquireHostLock(Host host, Cluster cluster) {
        this.host = host;
        this.cluster = cluster;
        provideDetailArgs(host.getHostName(), host.getId());
    }

    @Override
    public void execute() throws Exception {
        acquireClusterLock();

        String lockName = host.getId().toString();
        if (!ExecutionUtils.acquireLock(lockName)) {
        	throw stateException("AcquireHostLock.illegalState.failedHostLock", lockName);
        }
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
