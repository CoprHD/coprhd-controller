/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.Controller;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationArgument;

import java.net.URI;
import java.util.List;

public interface RemoteReplicationController extends Controller {
    public DriverTask createGroupReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId);
    public DriverTask createSetReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId);
    public DriverTask deleteReplicationPairs(List<URI> replicationPairs, String opId);

    // replication link operations
    public DriverTask start(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask stop(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask suspend(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask resume(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask split(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask failover(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask failback(RemoteReplicationArgument replicationArgument, String opId);
    public DriverTask swap(RemoteReplicationArgument replicationArgument, String opId);
}
