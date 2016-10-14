/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.volumecontroller.TaskCompleter;

public interface RemoteReplicationDevice {

    public void createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, TaskCompleter taskCompleter);
    public void createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, boolean createActive, TaskCompleter taskCompleter);
    public void deleteReplicationPair(URI replicationPair, TaskCompleter taskCompleter);

    // replication link operations
    public void start(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void stop(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void suspend(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void resume(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void split(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void synchronize(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void failover(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void failback(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);
    public void swap(RemoteReplicationArgument replicationArgument, TaskCompleter taskCompleter);

}
