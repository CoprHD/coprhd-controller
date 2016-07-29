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

    /**
     * Delete remote replication pairs. Should not delete backend volumes.
     * Only should affect remote replication configuration on array.
     *
     * @param replicationPairs replication pairs to delete
     * @return  driver task
     */
    public DriverTask deleteReplicationPairs(List<RemoteReplicationPair> replicationPairs);

    // replication link operations

    /**
     * Start replication link for remote replication argument.
     * @param replicationArgument replication argument: set/group/pair
     * @return driver task
     */
    public DriverTask start(RemoteReplicationArgument replicationArgument);

    /**
     * Stop remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask stop(RemoteReplicationArgument replicationArgument);

    /**
     * Suspend remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask suspend(RemoteReplicationArgument replicationArgument);

    /**
     * Resume remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask resume(RemoteReplicationArgument replicationArgument);

    /**
     * Split remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask split(RemoteReplicationArgument replicationArgument);

    /**
     * Failover remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask failover(RemoteReplicationArgument replicationArgument);

    /**
     * Failback remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask failback(RemoteReplicationArgument replicationArgument);

    /**
     * Swap remote replication link for remote replication argument
     * @param replicationArgument: set/group/pair
     * @return driver task
     */
    public DriverTask swap(RemoteReplicationArgument replicationArgument);

    /**
     * Move replication pair from its parent group to other replication group.
     * @param replicationPair replication pair to move
     * @param targetGroup new parent replication group for the pair
     * @return driver task
     */
    public DriverTask movePair(RemoteReplicationPair replicationPair, RemoteReplicationGroup targetGroup);
}
