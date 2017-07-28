/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.Controller;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredSystemObject;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.exceptions.ClientControllerException;
import com.emc.storageos.impl.AbstractDiscoveredSystemController;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl;
import com.emc.storageos.volumecontroller.impl.ControllerServiceImpl.Lock;
import com.emc.storageos.volumecontroller.impl.Dispatcher;

public class NetworkControllerImpl extends AbstractDiscoveredSystemController implements NetworkController {

    private static final Logger _log = LoggerFactory.getLogger(NetworkControllerImpl.class);
    private Set<NetworkController> _deviceImpl;
    private Dispatcher _dispatcher;
    private DbClient _dbClient;

    public void setDeviceImpl(Set<NetworkController> deviceImpl) {
        _deviceImpl = deviceImpl;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        _dispatcher = dispatcher;
    }

    public void setDbClient(DbClient dbClient) {
        _dbClient = dbClient;
    }

    @Override
    public void connectNetwork(URI network) throws InternalException {
        execNetwork("connectNetwork", network);
    }

    @Override
    public void disconnectNetwork(URI network) throws InternalException {
        execNetwork("disconnectNetwork", network);
    }

    @Override
    public void discoverNetworkSystems(AsyncTask[] tasks)
            throws InternalException {
        try {
            ControllerServiceImpl.scheduleDiscoverJobs(tasks, Lock.NS_DATA_COLLECTION_LOCK, ControllerServiceImpl.NS_DISCOVERY);
        } catch (Exception e) {
            _log.error("Problem in discoverStorageSystem due to {} ",
                    e.getMessage());
            throw ClientControllerException.fatals.unableToScheduleDiscoverJobs(tasks, e);
        }
    }

    private void execNetwork(String methodName, Object... args)
            throws InternalException {
        queueTask(_dbClient, NetworkSystem.class, _dispatcher, methodName, args);
    }

    public Controller lookupDeviceController(DiscoveredSystemObject device) {
        // dummy impl that returns the first one
        return _deviceImpl.iterator().next();
    }

    @Override
    public void testCommunication(URI network, String taskId)
            throws InternalException {
        execNetwork("testCommunication", network, taskId);
    }

    @Override
    public List<String> getFabricIds(URI network) throws InternalException {
        try {
            NetworkSystem device = _dbClient.queryObject(NetworkSystem.class, network);
            NetworkDeviceController devController = (NetworkDeviceController) lookupDeviceController(device);
            return devController.getFabricIds(network);
        } catch (InternalException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("Network Device Controller");
        }
    }

    @Override
    public List<Zoneset> getZonesets(URI network, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
            boolean excludeAliases) throws InternalException {
        try {
            NetworkSystem device = _dbClient.queryObject(NetworkSystem.class, network);
            NetworkDeviceController devController = (NetworkDeviceController) lookupDeviceController(device);
            return devController.getZonesets(network, fabricId, fabricWwn, zoneName, excludeMembers, excludeAliases);
        } catch (InternalException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("Network Device Controller");
        }
    }

    @Override
    public void addSanZones(URI network, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones,
            String taskId) throws InternalException {
        execNetwork("addSanZones", network, fabricId, fabricWwn, zones, activateZones, taskId);
    }

    @Override
    public void createSanZones(List<URI> initiatorUris, Map<URI, List<URI>> generatedIniToStoragePort,
            String taskId) throws ControllerException {
        execNetwork("createSanZones", initiatorUris, generatedIniToStoragePort, taskId);
    }

    @Override
    public void removeSanZones(URI network, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones,
            String taskId) throws InternalException {
        execNetwork("removeSanZones", network, fabricId, fabricWwn, zones, activateZones, taskId);
    }

    @Override
    public void updateSanZones(URI network, String fabricId, String fabricWwn, List<ZoneUpdate> zones, boolean activateZones,
            String taskId) throws InternalException {
        execNetwork("updateSanZones", network, fabricId, fabricWwn, zones, activateZones, taskId);
    }

    @Override
    public void activateSanZones(URI network, String fabricId, String fabricWwn, String taskId) throws InternalException {
        execNetwork("activateSanZones", network, fabricId, fabricWwn, taskId);
    }

    @Override
    public void deleteNetworkSystem(URI network, String taskId)
            throws InternalException {
        execNetwork("deleteNetworkSystem", network, taskId);
    }

    @Override
    public List<ZoneWwnAlias> getAliases(URI network, String fabricId, String fabricWwn) throws InternalException {
        try {
            NetworkSystem device = _dbClient.queryObject(NetworkSystem.class, network);
            NetworkDeviceController devController = (NetworkDeviceController) lookupDeviceController(device);
            return devController.getAliases(network, fabricId, fabricWwn);
        } catch (InternalException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ClientControllerException.fatals.unableToLocateDeviceController("Network Device Controller");
        }
    }

    @Override
    public void addAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws InternalException {
        execNetwork("addAliases", network, fabricId, fabricWwn, aliases, taskId);
    }

    @Override
    public void removeAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws InternalException {
        execNetwork("removeAliases", network, fabricId, fabricWwn, aliases, taskId);
    }

    @Override
    public void updateAliases(URI network, String fabricId, String fabricWwn, List<ZoneWwnAliasUpdate> updateAliases, String taskId)
            throws InternalException {
        execNetwork("updateAliases", network, fabricId, fabricWwn, updateAliases, taskId);
    }
}
