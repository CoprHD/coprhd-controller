/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.externaldevice;


import java.net.URI;
import java.util.List;

import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationPair;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.externaldevice.taskcompleters.RemoteReplicationFailoverCompleter;

public interface RemoteReplicationDevice {

    public void createRemoteReplicationGroup(URI groupURI, List<URI> sourcePorts, List<URI> targetPorts, TaskCompleter taskCompleter);
    public void createGroupReplicationPairs(List<RemoteReplicationPair> replicationPairs, TaskCompleter taskCompleter);
    public void createSetReplicationPairs(List<RemoteReplicationPair> replicationPairs, TaskCompleter taskCompleter);
    public void deleteReplicationPairs(List<URI> replicationPairs, TaskCompleter taskCompleter);
    public void movePair(URI replicationPair, URI targetGroup, TaskCompleter taskCompleter);

    // replication link operations
    public void establish(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void suspend(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void resume(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void split(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void failover(RemoteReplicationElement replicationArgument, RemoteReplicationFailoverCompleter taskCompleter);
    public void failback(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void swap(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void stop(RemoteReplicationElement replicationArgument, TaskCompleter taskCompleter);
    public void changeReplicationMode(RemoteReplicationElement replicationElement, String newMode, TaskCompleter taskCompleter);


}
