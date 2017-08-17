/*
 * Copyright (c) 2017 Dell EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.remotereplicationcontroller;


import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.remotereplication.RemoteReplicationGroup;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.filereplicationcontroller.FileReplicationController;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.volumecontroller.impl.externaldevice.RemoteReplicationElement;

import java.net.URI;
import java.util.List;
import java.util.Set;

public class RemoteReplicationControllerImpl implements RemoteReplicationController {

    private final static String REMOTE_REPLICATION_DEVICE = "remote-replication-device";
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

    protected Controller getController() {
        return deviceControllers.iterator().next();
    }

    private void exec(String methodName, Object... args) throws ControllerException {
            dispatcher.queue(NullColumnValueGetter.getNullURI(), REMOTE_REPLICATION_DEVICE,
                    getController(), methodName, args);
    }

    @Override
    public void createRemoteReplicationGroup(URI replicationGroup, List<URI> sourcePorts, List<URI> targetPorts, String opId) {
        RemoteReplicationGroup rrGroup = dbClient.queryObject(RemoteReplicationGroup.class, replicationGroup);
        exec("createRemoteReplicationGroup", replicationGroup, sourcePorts, targetPorts, opId);

    }


    @Override
    public void deleteReplicationPairs(List<URI> replicationPairs, String opId) {

    }

    @Override
    public void suspend(RemoteReplicationElement replicationElement, String opId) {
        exec("suspend", replicationElement, opId);
    }

    @Override
    public void resume(RemoteReplicationElement replicationElement, String opId) {
        exec("resume", replicationElement, opId);
    }

    @Override
    public void split(RemoteReplicationElement replicationElement, String opId) {
        exec("split", replicationElement, opId);
    }

    @Override
    public void stop(RemoteReplicationElement replicationElement, String opId) {
        exec("stop", replicationElement, opId);
    }

    @Override
    public void establish(RemoteReplicationElement replicationElement, String opId) {
        exec("establish", replicationElement, opId);
    }

    @Override
    public void failover(RemoteReplicationElement replicationElement, String opId) {
        exec("failover", replicationElement, opId);
    }

    @Override
    public void failback(RemoteReplicationElement replicationElement, String opId) {
        exec("failback", replicationElement, opId);
    }

    @Override
    public void swap(RemoteReplicationElement replicationElement, String opId) {
        exec("swap", replicationElement, opId);
    }

    @Override
    public void changeReplicationMode(RemoteReplicationElement replicationElement, String newRemoteReplicationMode, String opId) {
        exec("changeReplicationMode", replicationElement, newRemoteReplicationMode, opId);
    }

    @Override
    public void movePair(URI replicationPair, URI targetGroup, String opId) {
        exec("movePair", replicationPair, targetGroup, opId);
    }
}
