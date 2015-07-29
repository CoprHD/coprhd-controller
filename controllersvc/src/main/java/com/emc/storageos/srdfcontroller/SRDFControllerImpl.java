/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.srdfcontroller;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Set;

/**
 * South bound API implementation - a singleton instance
 * of this class services all SRDF calls.
 */
public class SRDFControllerImpl extends AbstractDiscoveredSystemController implements SRDFController {
    private static final Logger log = LoggerFactory.getLogger(SRDFControllerImpl.class);

    private Set<SRDFController> deviceImpl;
    private Dispatcher dispatcher;
    private DbClient dbClient;

    public Set<SRDFController> getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(Set<SRDFController> deviceImpl) {
        this.deviceImpl = deviceImpl;
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

    protected Controller lookupDeviceController(DiscoveredSystemObject storageSystem) {
        return deviceImpl.iterator().next();
    }

    private void execFS(String method, Object... args) throws InternalException {
        queueTask(dbClient, StorageSystem.class, dispatcher, method, args);
    }

    @Override
    public void connect(URI protection) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void disconnect(URI protection) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void discover(AsyncTask[] tasks) throws InternalException {
        // TODO Auto-generated method stub

    }

    @Override
    public void performProtectionOperation(URI system, URI id, String op, String task) throws InternalException {
        execFS("performProtectionOperation", system, id, op, task);

    }

    @Override
    public void expandVolume(URI storage, URI pool, URI volumeId, Long size, String token) throws InternalException {
        execFS("expandVolume", storage, pool, volumeId, size, token);

    }

}
