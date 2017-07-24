/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.computecontroller.impl;

import java.net.URI;
import java.util.Set;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.computecontroller.ComputeController;
import com.emc.storageos.computesystemcontroller.exceptions.ComputeSystemControllerException;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.ComputeElement;
import com.emc.storageos.db.client.model.ComputeSystem;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.Host;
import com.emc.storageos.db.client.util.NullColumnValueGetter;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class ComputeControllerImpl extends AbstractDiscoveredSystemController implements ComputeController {

    private static final Logger _log = LoggerFactory.getLogger(ComputeControllerImpl.class);
    private Set<ComputeDeviceController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<ComputeDeviceController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    private void execCompute(String methodName, Object... args) {
        queueTask(_dbClient, ComputeSystem.class, _dispatcher, methodName, args);
    }

    @Override
    public void discoverComputeSystems(AsyncTask[] tasks)
            throws InternalException {
        _log.info("discoverComputeSystems");
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, ControllerServiceImpl.Lock.COMPUTE_DATA_COLLECTION_LOCK,
                    ControllerServiceImpl.COMPUTE_DISCOVERY);
        } catch (Exception e) {
            _log.error("Problem in discoverStorageSystem due to {} ",
                    e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

    @Override
    public void createHosts(URI varray, URI vcpoolId,Map<Host,URI> hostsMap, AsyncTask[] tasks) throws InternalException {
        _log.info("createHosts");
        for (AsyncTask task : tasks) {

            Host host = _dbClient.queryObject(Host.class, task._id);
            URI ceURI = null;

            if (host != null) {
                if (hostsMap != null || !hostsMap.isEmpty()) {
                   for (Host h : hostsMap.keySet()){
                       _log.info("Host: "+ h.getLabel() + "CE: "+ hostsMap.get(h).toString());
                       if (h.getId().equals(host.getId())) {
                           ceURI = hostsMap.get(h);
                       }
                   }
                } 


                if (!NullColumnValueGetter.isNullURI(ceURI)) {
                    ComputeElement computeElement = _dbClient.queryObject(ComputeElement.class,
                            ceURI);
                    execCompute("createHost", vcpoolId, varray, computeElement, task._id, task._opId);
                } else {
                    _dbClient.error(Host.class, task._id, task._opId, ComputeSystemControllerException.exceptions
                            .noComputeElementAssociatedWithHost(host.getNativeGuid().toString(), host.getId()
                                    .toString(), null));
                }
            } else {
                // This should not ever occur! but logging and skipping for now
                // if the host comes out to be null
                _log.error("CreateHost task does not reference any valid Host! -- Skipping");
                continue;
            }
        }
    }

    @Override
    protected Controller lookupDeviceController(DiscoveredSystemObject device) {
        if (device == null) {
            throw ClientControllerException.fatals.unableToLookupStorageDeviceIsNull();
        }
        ComputeDeviceController cc = _deviceImpl.iterator().next();
        if (cc == null) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("ComputeController");
        }
        return cc;
    }

    @Override
    public void clearDeviceSession(URI computeSystemId) throws InternalException {
        ComputeSystem cs = _dbClient.queryObject(ComputeSystem.class, computeSystemId);
        execCompute("clearDeviceSession", cs.getId());
    }

    @Override
    public void deactivateHost(AsyncTask[] tasks) throws InternalException {

        AsyncTask task = tasks[0];

        Host host = _dbClient.queryObject(Host.class, task._id);

        if (host != null) {
            if (!NullColumnValueGetter.isNullURI(host.getComputeElement())) {
                ComputeElement computeElement = _dbClient.queryObject(
                        ComputeElement.class, host.getComputeElement());
                execCompute("deactivateHost", computeElement.getComputeSystem(),
                        task._id, task._opId);
            } else {
                _dbClient.error(Host.class, task._id, task._opId,
                        ComputeSystemControllerException.exceptions
                        .noComputeElementAssociatedWithHost(host
                                .getNativeGuid().toString(), host
                                .getId().toString(), null));
            }

        }
    }
}
