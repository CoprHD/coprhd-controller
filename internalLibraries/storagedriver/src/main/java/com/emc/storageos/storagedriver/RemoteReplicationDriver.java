/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationTarget;

import java.util.List;

public interface RemoteReplicationDriver {

    public DriverTask createReplicationGroup(RemoteReplicationGroup replicationGroup);
    public DriverTask createReplicationPair(RemoteReplicationPair replicationPair);
    public DriverTask deleteReplicationGroup(RemoteReplicationGroup replicationGroup);
    public DriverTask deleteReplicationPair(RemoteReplicationPair replicationPair);

    // replication link (target) operations
    // We use multiple targets in the call to support create for multiple replicated volumes.
    // Only drivers has details how to orchestrate this operation on device.
    public DriverTask start(List<RemoteReplicationTarget> replicationTargetList);
    public DriverTask stop(RemoteReplicationTarget replicationTarget);
    public DriverTask suspend(RemoteReplicationTarget replicationTarget);
    public DriverTask resume(RemoteReplicationTarget replicationTarget);
    public DriverTask split(RemoteReplicationTarget replicationTarget);
    public DriverTask failover(RemoteReplicationTarget replicationTarget);
    public DriverTask failback(RemoteReplicationTarget replicationTarget);
    public DriverTask swap(RemoteReplicationTarget replicationTarget);
}
