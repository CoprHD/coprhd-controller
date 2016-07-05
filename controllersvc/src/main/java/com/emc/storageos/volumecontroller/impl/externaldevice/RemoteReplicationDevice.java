/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;
import java.util.List;

import com.emc.storageos.volumecontroller.TaskCompleter;

public interface RemoteReplicationDevice {

    public void createGroupReplicationPairs(List<URI> replicationPairs, boolean createActive);
    public void createSetReplicationPairs(List<URI> replicationPairs, boolean createActive);
    public void deleteReplicationPairs(List<URI> replicationPairs);

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
