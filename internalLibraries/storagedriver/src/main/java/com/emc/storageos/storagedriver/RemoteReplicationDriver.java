/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationTarget;

public interface RemoteReplicationDriver {

    public DriverTask createReplicationGroup(RemoteReplicationGroup replicationGroup);
    public DriverTask createReplicationPair(RemoteReplicationPair replicationPair);
    public DriverTask deleteReplicationGroup(RemoteReplicationGroup replicationGroup);
    public DriverTask deleteReplicationPair(RemoteReplicationPair replicationPair);

    // replication link (target) operations
    public DriverTask start(RemoteReplicationTarget replicationTarget);
    public DriverTask stop(RemoteReplicationTarget replicationTarget);
    public DriverTask pause(RemoteReplicationTarget replicationTarget);
    public DriverTask resume(RemoteReplicationTarget replicationTarget);
    public DriverTask failover(RemoteReplicationTarget replicationTarget);
    public DriverTask failback(RemoteReplicationTarget replicationTarget);
    public DriverTask swap(RemoteReplicationTarget replicationTarget);
}
