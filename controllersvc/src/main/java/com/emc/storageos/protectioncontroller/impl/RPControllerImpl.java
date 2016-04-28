/*
 * Copyright (c) 2008-2011 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.protectioncontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.ProtectionSystem;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.protectioncontroller.RPController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.ApplicationAddVolumeList;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

/**
 * South bound API implementation - a singleton instance
 * of this class services all protection calls. Protection
 * calls are matched against device specific controller implementations
 * and forwarded from this implementation
 */
public class RPControllerImpl extends AbstractDiscoveredSystemController implements RPController {
    private final static Logger _log = LoggerFactory.getLogger(RPControllerImpl.class);

    // device specific RPController implementations
    private Set<RPController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<RPController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject storageSystem) {
        // dummy impl that returns the first one
        return _deviceImpl.iterator().next();
    }

    private void execFS(String methodName, Object... args) throws InternalException {
        queueTask(_dbClient, ProtectionSystem.class, _dispatcher, methodName, args);
    }

    @Override
    public void connect(URI protection) throws InternalException {
        execFS("connect", protection);
    }

    @Override
    public void disconnect(URI protection) throws InternalException {
        execFS("disconnect", protection);
    }

    @Override
    public void performProtectionOperation(URI protectionDevice, URI id,
            URI copyID, String pointInTime, String op, String task) throws InternalException {
        execFS("performProtectionOperation", protectionDevice, id, copyID, pointInTime, op, task);
    }

    @Override
    public void
            updateConsistencyGroupPolicy(URI protectionDevice, URI consistencyGroup, List<URI> volumeURIs, URI newVpoolURI, String task)
                    throws InternalException {
        execFS("updateConsistencyGroupPolicy", protectionDevice, consistencyGroup, volumeURIs, newVpoolURI, task);
    }

    @Override
    public void createSnapshot(URI protectionDevice, URI storageDevice, List<URI> snapshotList, Boolean createInactive, Boolean readOnly,
            String opId) throws InternalException {
        execFS("createSnapshot", protectionDevice, storageDevice, snapshotList, createInactive, readOnly, opId);
    }

    @Override
    public void discover(AsyncTask[] tasks) throws ControllerException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.DISCOVER_COLLECTION_LOCK, ControllerServiceImpl.DISCOVERY);
        } catch (Exception e) {
            _log.error(
                    "Problem in discoverProtectionSystem due to {} ",
                    e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

    @Override
    public void deleteSnapshot(URI protectionDevice, URI snapshot, String task) throws InternalException {
        execFS("deleteSnapshot", protectionDevice, snapshot, task);
    }

    /* (non-Javadoc)
     * @see com.emc.storageos.protectioncontroller.RPController#updateApplication(java.net.URI, com.emc.storageos.volumecontroller.ApplicationAddVolumeList, java.util.List, java.net.URI, java.lang.String)
     */
    @Override
    public void updateApplication(URI systemURI, ApplicationAddVolumeList addVolumesNotInCG, List<URI> removeVolumesURI, URI applicationId,
            String taskId) {
        execFS("updateApplication", systemURI, addVolumesNotInCG, removeVolumesURI, applicationId, taskId);
        
    }
}
