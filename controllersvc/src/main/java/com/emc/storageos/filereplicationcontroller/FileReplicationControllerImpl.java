/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.filereplicationcontroller;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;

/**
 * South bound API implementation - a singleton instance
 * of this class services all replication operation calls
 */
public class FileReplicationControllerImpl extends AbstractDiscoveredSystemController implements FileReplicationController {

    private static final Logger log = LoggerFactory.getLogger(FileReplicationControllerImpl.class);

    private Set<FileReplicationController> deviceImpl;
    private Dispatcher dispatcher;
    private DbClient dbClient;

    public Set<FileReplicationController> getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(Set<FileReplicationController> deviceImpl) {
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

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject device) {
        // TODO Auto-generated method stub
        return deviceImpl.iterator().next();
    }

    @Override
    public void performNativeContinuousCopies(URI storage, URI sourceFileShare,
            List<URI> mirrorURIs, String opType, String opId)
            throws ControllerException {
        // TODO Auto-generated method stub
        execFS("performNativeContinuousCopies", storage, sourceFileShare, mirrorURIs, opType, opId);
    }

    @Override
    public void performRemoteContinuousCopies(URI storage, URI copyId,
            String opType, String opId) throws ControllerException {
        // TODO Auto-generated method stub
        execFS("performRemoteContinuousCopies", storage, copyId, opType, opId);
    }

    private void execFS(String method, Object... args) throws InternalException {
        queueTask(dbClient, StorageSystem.class, dispatcher, method, args);
    }

}
