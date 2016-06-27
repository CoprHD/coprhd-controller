/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import com.emc.storageos.volumecontroller.TaskCompleter;

import java.util.List;

public interface RemoteReplicationDevice {

    // replication link (target) operations
    public void start(List<RemoteReplicationTarget> replicationTargetList, TaskCompleter taskCompleter);
    public void stop(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void suspend(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void resume(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void split(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void synchronize(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void failover(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void failback(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);
    public void swap(RemoteReplicationTarget replicationTarget, TaskCompleter taskCompleter);

}
