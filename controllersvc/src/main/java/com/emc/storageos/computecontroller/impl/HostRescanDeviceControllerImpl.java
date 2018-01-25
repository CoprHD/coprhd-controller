/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;

import com.emc.storageos.Controller;
import com.emc.storageos.computecontroller.HostRescanController;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class HostRescanDeviceControllerImpl extends AbstractDiscoveredSystemController implements HostRescanController {
    private Dispatcher dispatcher;
    private DbClient dbClient;
    private HostRescanController deviceImpl;

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

    public HostRescanController getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(HostRescanController deviceImpl) {
        this.deviceImpl = deviceImpl;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject device) {
        return deviceImpl;
    }

    /**
     * Puts the operation in the zkQueue so it can dispatched to a Device Controller.
     * 
     * @param methodName
     * @param args
     * @throws InternalException
     */
    private void blockRMI(String methodName, Object... args) throws InternalException {
        queueTask(dbClient, Host.class, dispatcher, methodName, args);
    }

    @Override
    public void rescanHostStorage(URI hostId, String taskId) {
        blockRMI("rescanHostStorage", hostId, taskId);
    }

    @Override
    public void rescanHostStoragePaths(URI hostId, String taskId) {
        blockRMI("rescanHostStoragePaths", hostId, taskId);
    }

}
