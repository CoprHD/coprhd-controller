/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.sa.service.vipr.tasks;

import com.emc.sa.engine.ExecutionTask;
import com.emc.sa.engine.ExecutionUtils;
import com.emc.storageos.db.client.model.Cluster;
import com.emc.storageos.db.client.model.Host;

public class ReleaseHostLock extends ExecutionTask<Void> {
    private Host host;
    private Cluster cluster;

    public ReleaseHostLock(Host host) {
        this(host, null);
    }

    public ReleaseHostLock(Host host, Cluster cluster) {
        this.host = host;
        this.cluster = cluster;
        provideDetailArgs(host.getHostName());
    }

    @Override
    public void execute() throws Exception {
        acquireClusterLock();

        String lockName = host.getId().toString();
        ExecutionUtils.releaseLock(lockName);
    }

    private void acquireClusterLock() {
        if (cluster != null) {
            String lockName = cluster.getId().toString();
            ExecutionUtils.releaseLock(lockName);
        }
    }
}
