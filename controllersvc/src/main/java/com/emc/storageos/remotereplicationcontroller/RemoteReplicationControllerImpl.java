/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remotereplicationcontroller;


import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.filereplicationcontroller.FileReplicationController;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

import java.net.URI;
import java.util.List;
import java.util.Set;

public class RemoteReplicationControllerImpl extends AbstractDiscoveredSystemController implements RemoteReplicationController {

    private Set<RemoteReplicationController> deviceControllers;
    private Dispatcher dispatcher;
    private DbClient dbClient;

    public Set<RemoteReplicationController> getDeviceControllers() {
        return deviceControllers;
    }

    public void setDeviceControllers(Set<RemoteReplicationController> deviceControllers) {
        this.deviceControllers = deviceControllers;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public DbClient getDbClient() {
        return dbClient;
    }

    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject device) {
        return deviceControllers.iterator().next();
    }

    private void queueRequest(String methodName, Object... args) throws InternalException {
        queueTask(dbClient, StorageSystem.class, dispatcher, methodName, args);
    }


    @Override
    public void createRemoteReplicationGroup(URI replicationGroup, String opId) {
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, replicationGroup);
        queueRequest("createRemoteReplicationGroup", rrGroup.getSourceSystem(), replicationGroup, opId);

    }

    @Override
    public void createGroupReplicationPairs(List<URI> replicationPairs, String opId) {

    }

    @Override
    public void createSetReplicationPairs(List<URI> replicationPairs, String opId) {

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
