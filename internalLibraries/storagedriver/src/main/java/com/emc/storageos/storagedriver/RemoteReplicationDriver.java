/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.storagedriver;


import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.storagedriver.model.remotereplication.RemoteReplicationArgument;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

import java.util.List;

public interface RemoteReplicationDriver {

    /**
     * Create remote replication group.
     * @param replicationGroup specifies properties of remote replication group to create.
     * @param capabilities storage capabilities for the group
     * @return driver task
     */
    public DriverTask createRemoteReplicationGroup(RemoteReplicationGroup replicationGroup, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication group.
     *
     * @param replicationPairs list of replication pairs to create
     * @param createActive true, if pair should start replication link automatically after creation, false otherwise
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, StorageCapabilities capabilities);

    /**
     * Create replication pairs in existing replication set. Pairs are created outside of group container.
     *
     * @param replicationPairs list of replication pairs to create
     * @param createActive true, if pair should start replication link automatically after creation, false otherwise
     * @param capabilities storage capabilities for the pairs
     * @return driver task
     */
    public DriverTask createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, StorageCapabilities capabilities);

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
