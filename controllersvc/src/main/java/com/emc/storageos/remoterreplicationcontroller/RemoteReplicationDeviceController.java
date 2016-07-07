/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationArgument;

import java.net.URI;
import java.util.List;

public class RemoteReplicationDeviceController implements RemoteReplicationController {
    @Override
    public DriverTask createGroupReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {
        return null;
    }

    @Override
    public DriverTask createSetReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {
        return null;
    }

    @Override
    public DriverTask deleteReplicationPairs(List<URI> replicationPairs, String opId) {
        return null;
    }

    @Override
    public DriverTask start(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask stop(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask suspend(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask resume(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask split(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask failover(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask failback(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }

    @Override
    public DriverTask swap(RemoteReplicationArgument replicationArgument, String opId) {
        return null;
    }
}
