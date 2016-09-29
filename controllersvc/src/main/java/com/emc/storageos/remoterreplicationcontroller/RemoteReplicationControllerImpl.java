/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remoterreplicationcontroller;


import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationArgument;

import java.net.URI;
import java.util.List;

public class RemoteReplicationControllerImpl implements RemoteReplicationController {
    @Override
    public void createRemoteReplicationGroup(URI replicationGroup, String opId) {

    }

    @Override
    public void createGroupReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {

    }

    @Override
    public void createSetReplicationPairs(List<URI> replicationPairs, boolean createActive, String opId) {

    }

    @Override
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId) {

    }

    @Override
    public void suspend(URI replicationArgument, String opId) {

    }

    @Override
    public void resume(URI replicationArgument, String opId) {

    }

    @Override
    public void split(URI replicationArgument, String opId) {

    }

    @Override
    public void establish(URI replicationArgument, String opId) {

    }

    @Override
    public void failover(URI replicationArgument, String opId) {

    }

    @Override
    public void failback(URI replicationArgument, String opId) {

    }

    @Override
    public void swap(URI replicationArgument, String opId) {

    }

    @Override
    public void movePair(URI replicationPair, URI targetGroup, String opId) {

    }
}
