/*
 * Copyright (c) 2017 Dell EMC
 * All Rights Reserved
 */
package com.emc.storageos.migrationcontroller;

import java.net.URI;
import java.util.Set;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class MigrationControllerImpl extends AbstractDiscoveredSystemController implements MigrationController {
    private Dispatcher dispatcher;
    private DbClient dbClient;
    private Set<MigrationController> deviceImpl;

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

    public Set<MigrationController> getDeviceImpl() {
        return deviceImpl;
    }

    public void setDeviceImpl(Set<MigrationController> deviceImpl) {
        this.deviceImpl = deviceImpl;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject storageSystem) {
        return deviceImpl.iterator().next();
    }

    /**
     * Puts the operation in the zkQueue so it can dispatched to a Device Controller.
     * 
     * @param methodName
     * @param args
     * @throws InternalException
     */
    private void blockRMI(String methodName, Object... args) throws InternalException {
        queueTask(dbClient, StorageSystem.class, dispatcher, methodName, args);
    }

    @Override
    public void migrationCreateEnvironment(URI sourceSystemURI, URI targetSystemURI, String taskId) throws ControllerException {
        blockRMI("migrationCreateEnvironment", sourceSystemURI, targetSystemURI, taskId);
    }

    @Override
    public void migrationCreate(URI sourceSystemURI, URI cgURI, URI migrationURI, URI targetSystemURI, String taskId)
            throws ControllerException {
        blockRMI("migrationCreate", sourceSystemURI, cgURI, migrationURI, targetSystemURI, taskId);
    }

    @Override
    public void migrationCutover(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationCutover", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationCommit(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationCommit", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationCancel(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationCancel", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationRefresh(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationRefresh", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationRecover(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationRecover", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationSyncStop(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationSyncStop", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationSyncStart(URI sourceSystemURI, URI cgURI, URI migrationURI, String taskId) throws ControllerException {
        blockRMI("migrationSyncStart", sourceSystemURI, cgURI, migrationURI, taskId);
    }

    @Override
    public void migrationRemoveEnvironment(URI sourceSystemURI, URI targetSystemURI, String taskId) throws ControllerException {
        blockRMI("migrationRemoveEnvironment", sourceSystemURI, targetSystemURI, taskId);
    }

}
