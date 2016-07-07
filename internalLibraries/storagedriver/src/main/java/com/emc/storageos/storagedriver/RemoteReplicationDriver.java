/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationArgument;

import java.util.List;

public interface RemoteReplicationDriver {

    public DriverTask createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive);
    public DriverTask createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive);
    public DriverTask deleteReplicationPairs(List<RemoteReplicationPair> replicationPairs);

    // replication link operations
    public DriverTask start(RemoteReplicationArgument replicationArgument);
    public DriverTask stop(RemoteReplicationArgument replicationArgument);
    public DriverTask suspend(RemoteReplicationArgument replicationArgument);
    public DriverTask resume(RemoteReplicationArgument replicationArgument);
    public DriverTask split(RemoteReplicationArgument replicationArgument);
    public DriverTask failover(RemoteReplicationArgument replicationArgument);
    public DriverTask failback(RemoteReplicationArgument replicationArgument);
    public DriverTask swap(RemoteReplicationArgument replicationArgument);
}
