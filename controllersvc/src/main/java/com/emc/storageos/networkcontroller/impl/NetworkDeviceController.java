/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.networkcontroller.impl;

import java.io.IOException;
import java.net.URI;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;

import org.apache.curator.framework.recipes.locks.InterProcessLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.coordinator.client.service.CoordinatorClient;
import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.DbModelClient;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.BlockObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.ExportGroup;
import com.emc.storageos.db.client.model.ExportMask;
import com.emc.storageos.db.client.model.FCEndpoint;
import com.emc.storageos.db.client.model.FCZoneReference;
import com.emc.storageos.db.client.model.HostInterface;
import com.emc.storageos.db.client.model.Initiator;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.NetworkSystem;
import com.emc.storageos.db.client.model.Operation;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol.Transport;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.VirtualArray;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.db.client.model.ZoneInfo;
import com.emc.storageos.db.client.model.ZoneInfoMap;
import com.emc.storageos.db.client.util.CommonTransformerFunctions;
import com.emc.storageos.db.client.util.DataObjectUtils;
import com.emc.storageos.db.client.util.StringMapUtil;
import com.emc.storageos.db.client.util.StringSetUtil;
import com.emc.storageos.db.client.util.WWNUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.db.joiner.Joiner;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.model.ResourceOperationTypeEnum;
import com.emc.storageos.networkcontroller.NetworkController;
import com.emc.storageos.networkcontroller.NetworkFCContext;
import com.emc.storageos.networkcontroller.NetworkFCZoneInfo;
import com.emc.storageos.networkcontroller.exceptions.NetworkDeviceControllerException;
import com.emc.storageos.networkcontroller.impl.mds.Zone;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember;
import com.emc.storageos.networkcontroller.impl.mds.ZoneMember.ConnectivityMemberType;
import com.emc.storageos.networkcontroller.impl.mds.ZoneUpdate;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAlias;
import com.emc.storageos.networkcontroller.impl.mds.ZoneWwnAliasUpdate;
import com.emc.storageos.networkcontroller.impl.mds.Zoneset;
import com.emc.storageos.security.audit.AuditLogManager;
import com.emc.storageos.services.OperationTypeEnum;
import com.emc.storageos.svcs.errorhandling.model.ServiceCoded;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.util.ExportUtils;
import com.emc.storageos.util.NetworkLite;
import com.emc.storageos.util.NetworkUtil;
import com.emc.storageos.volumecontroller.AsyncTask;
import com.emc.storageos.volumecontroller.ControllerException;
import com.emc.storageos.volumecontroller.impl.BiosCommandResult;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableBourneEvent;
import com.emc.storageos.volumecontroller.impl.monitoring.RecordableEventManager;
import com.emc.storageos.volumecontroller.impl.monitoring.cim.enums.RecordType;
import com.emc.storageos.volumecontroller.impl.utils.ExportMaskUtils;
import com.emc.storageos.workflow.Workflow;
import com.emc.storageos.workflow.WorkflowService;
import com.emc.storageos.workflow.WorkflowStepCompleter;
import com.google.common.collect.Collections2;

public class NetworkDeviceController implements NetworkController {

    private DbClient _dbClient;
    private CoordinatorClient _coordinator;
    private static final Logger _log = LoggerFactory.getLogger(NetworkDeviceController.class);
    private Map<String, NetworkSystemDevice> _devices;
    private NetworkScheduler _networkScheduler;
    private static final String EVENT_SERVICE_TYPE = "network";
    private static final String EVENT_SERVICE_SOURCE = "NetworkDeviceController";

    @Autowired
    private AuditLogManager _auditMgr;
    @Autowired
    private DbModelClient dbModelClient;
    @Autowired
    private DataSourceFactory dataSourceFactory;
    @Autowired
    private CustomConfigHandler customConfigHandler;

    private RecordableEventManager _eventManager;

    public void setEventManager(RecordableEventManager eventManager) {
        _eventManager = eventManager;
    }

    public void setDbClient(DbClient dbc) {
        _dbClient = dbc;
    }

    public void setCoordinator(CoordinatorClient coordinator) {
        _coordinator = coordinator;
    }

    public void setDevices(Map<String, NetworkSystemDevice> deviceInterfaces) {
        _devices = deviceInterfaces;
    }

    private NetworkSystemDevice getDevice(String deviceType) {
        return _devices.get(deviceType);
    }

    public void setNetworkScheduler(NetworkScheduler networkScheduler) {
        _networkScheduler = networkScheduler;
    }

    public NetworkScheduler getNetworkScheduler() {
        return _networkScheduler;
    }

    /**
     * Returns the NetworkDevice from the db
     * 
     * @param network device URI
     * @return NetworkDevice
     * @throws ControllerException
     */
    private NetworkSystem getDeviceObject(URI network) throws ControllerException {
        NetworkSystem networkDev = null;
        try {
            networkDev = _dbClient.queryObject(NetworkSystem.class, network);
        } catch (Exception e) {
            throw NetworkDeviceControllerException.exceptions.getDeviceObjectFailedNotFound(
                    network.toString(), e);
        }

        // Verify non-null network device returned from the database client.
        if (networkDev == null) {
            throw NetworkDeviceControllerException.exceptions.getDeviceObjectFailedNull(
                    network.toString());
        }
        return networkDev;
    }

    /**
     * Save the NetworkSystem object after updates.
     * 
     * @param networkSystem
     * @throws ControllerException
     */
    private void saveDeviceObject(NetworkSystem networkSystem) throws ControllerException {
        try {
            _dbClient.persistObject(networkSystem);
        } catch (DatabaseException ex) {
            throw NetworkDeviceControllerException.exceptions.saveDeviceObjectFailed(
                    networkSystem.getId().toString(), ex);
        }
    }

    @Override
    public void connectNetwork(URI network) throws ControllerException {
        BiosCommandResult result = doConnect(network);
        boolean failed = false;
        if (!result.isCommandSuccess()) {
            _log.error("Connect failed to {}", network);
            failed = true;
            // To Do - mark device inactive or take in status to set failure.
        } else {
            String msg = MessageFormat.format("Connected to Network Device {0} at {1}", result.getMessage(), new Date());
            _log.info(msg);
        }
        // Update status on the NetworkSystem
        NetworkSystem networkObj = getDeviceObject(network);
        networkObj.setCompatibilityStatus(failed ?
                DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name()
                : DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        saveDeviceObject(networkObj);
    }

    private BiosCommandResult doConnect(URI network) throws ControllerException {
        // Retrieve the storage device info from the database.
        NetworkSystem networkObj = getDeviceObject(network);

        // Verify non-null network device returned from the database client.
        // Will not return null

        // Get the file device reference for the type of file device managed
        // by the controller.
        NetworkSystemDevice networkDevice = getDevice(networkObj.getSystemType());
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.doConnectFailed(
                    network.toString(), networkObj.getSystemType());
        }

        return networkDevice.doConnect(networkObj);
    }

    @Override
    public void disconnectNetwork(URI network) throws ControllerException {
        // Nothing to do
    }

    @Override
    public void discoverNetworkSystems(AsyncTask[] tasks)
            throws ControllerException {
        // this is now handled by the discovery framework
        throw new UnsupportedOperationException();
    }

    @Override
    public void testCommunication(URI network, String task)
            throws ControllerException {
        try {
            BiosCommandResult result = doConnect(network);
            if (result.isCommandSuccess()) {
                Operation op = new Operation();
                op.setMessage(result.getMessage());
                _dbClient.ready(NetworkSystem.class, network, task);
            } else {
                String opName = ResourceOperationTypeEnum.UPDATE_NETWORK.getName();
                ServiceError serviceError = NetworkDeviceControllerException.errors.testCommunicationFailed(opName,
                        network.toString());
                _dbClient.error(NetworkSystem.class, network, task, serviceError);
            }
        } catch (Exception e) {
            _log.error("Exception while trying update task status");
            try {
                String opName = ResourceOperationTypeEnum.UPDATE_NETWORK.getName();
                ServiceError serviceError = NetworkDeviceControllerException.errors.testCommunicationFailedExc(opName,
                        network.toString(), e);
                _dbClient.error(NetworkSystem.class, network, task, serviceError);
            } catch (DatabaseException ioe) {
                _log.error(ioe.getMessage());
            }
        }
    }

    @Override
    public List<String> getFabricIds(URI uri) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Get the file device reference for the type of file device managed
        // by the controller.
        NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.getFabricIdsFailedNull(device.getSystemType());
        }
        try {
            List<String> fabricIds = networkDevice.getFabricIds(device);
            return fabricIds;
        } catch (Exception ex) {
            Date date = new Date();
            throw NetworkDeviceControllerException.exceptions.getFabricIdsFailedExc(
                    uri.toString(), date.toString(), ex);
        }
    }

    @Override
    public List<Zoneset> getZonesets(URI uri, String fabricId, String fabricWwn, String zoneName, boolean excludeMembers,
            boolean excludeAliases) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Get the file device reference for the type of file device managed
        // by the controller.
        NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.getZonesetsFailedNull(device.getSystemType());
        }
        try {
            List<Zoneset> zonesets = networkDevice.getZonesets(device, fabricId, fabricWwn, zoneName, excludeMembers, excludeAliases);
            // NOTE! The RMI infrastructure doesn't know how to deal with CIMObjectPaths, even if they are in
            // Object pointers, so remove them here!
            for (Zoneset zs : zonesets) {
                zs.setCimObjectPath(null);
                for (Zone zo : zs.getZones()) {
                    zo.setCimObjectPath(null);
                    for (ZoneMember zm : zo.getMembers()) {
                        zm.setCimObjectPath(null);
                    }
                }
            }
            return zonesets;
        } catch (Exception ex) {
            Date date = new Date();
            throw NetworkDeviceControllerException.exceptions.getZonesetsFailedExc(uri.toString(), date.toString(), ex);
        }
    }

    @Override
    public void addSanZones(URI uri, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones,
            String taskId) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.addSanZonesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.addZones(device, zones, fabricId, fabricWwn, activateZones);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.ADD_SAN_ZONE, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.addSanZonesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    /**
     * Add/remove a group of zones as given by their NetworkFabricInfo structures.
     * ALL fabricInfos must be using the same NetworkDevice, and the same fabricId. There is a higher level
     * subroutine to split complex requests into sets of requests with the same NetworkDevice and fabricId.
     * 
     * @param device NetworkDevice
     * @param fabricId String
     * @param exportGroupUri The ExportGroup URI. Used for reference counting.
     * @param fabricInfos - Describe each zone.
     * @param activateZones - activate active zoneset after zones change
     * @param retryAltNetworkDevice - a boolean to indicate if re-try to be done.
     *            This is to stop this function from running again after the alternate
     *            system is retried once.
     * @return BiosCommandResult
     * @throws ControllerException
     */
    private BiosCommandResult addRemoveZones(NetworkSystem device, String fabricId, String fabricWwn,
            URI exportGroupUri, List<NetworkFCZoneInfo> fabricInfos, boolean doRemove,
            boolean retryAltNetworkDevice)
            throws ControllerException {

        BiosCommandResult result = null;
        String taskId = UUID.randomUUID().toString();
        List<Zone> zones = new ArrayList<Zone>();
        // Make the zone operations. Don't make the same zone more than once,
        // as determined by it's key. The same zone shows up multiple times because it
        // must be recorded for each volume in the FCZoneReference table.
        HashSet<String> keySet = new HashSet<String>();
        for (NetworkFCZoneInfo fabricInfo : fabricInfos) {
            String key = fabricInfo.makeEndpointsKey();
            if (false == keySet.contains(key)) {
                keySet.add(key);
                // neither create nor delete zones found on the switch
                if (fabricInfo.isExistingZone()) {
                    _log.info("Zone {} will not be created or removed on {}, as it is not vipr created. ", fabricInfo.getZoneName(),
                            fabricInfo.toString());
                    continue; // neither create nor delete zones found on the switch
                }
                // Don't actually remove the zone if it's not the last reference
                if (doRemove && !fabricInfo._isLastReference) {
                    _log.info("Zone {} will not be removed on {}, as still the zone is used to expose other volumes in export groups ",
                            fabricInfo.getZoneName(), fabricInfo.toString());
                    continue;
                }
                Zone zone = new Zone(fabricInfo.getZoneName());
                for (String address : fabricInfo.getEndPoints()) {
                    ZoneMember member = new ZoneMember(address, ConnectivityMemberType.WWPN);
                    zone.getMembers().add(member);
                }
                zones.add(zone);
            }
        }

        // Get the network device reference for the type of network device managed
        // by the controller.
        NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.addRemoveZonesFailedNull(
                    device.getSystemType());
        }

        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            if (doRemove) { /* Removing zones */
                result = networkDevice.removeZones(device, zones, fabricId, fabricWwn, true);
                if (result.isCommandSuccess()) {
                    String refKey = null;
                    try {
                        for (NetworkFCZoneInfo fabricInfo : fabricInfos) {
                            FCZoneReference ref = _dbClient.queryObject(FCZoneReference.class, fabricInfo.getFcZoneReferenceId());
                            if (ref != null) {
                                refKey = ref.getPwwnKey();
                                //Retrieve other zone references that have the same Port WWN. These zone references should be made
                                //inactive as well since the initiator is no longer available. 
                                URIQueryResultList queryResults = new URIQueryResultList();
                                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getFCZoneReferenceByWWNKey(refKey), queryResults);
                                Iterator<URI> queryResultsIter = queryResults.iterator();
                                while (queryResultsIter.hasNext()) {
                                	FCZoneReference refForWWNKey = _dbClient.queryObject(FCZoneReference.class, queryResultsIter.next());
                                    _dbClient.markForDeletion(refForWWNKey);
                                    _log.info(String.format("Remove FCZoneReference key: %s volume %s id %s",
                                            refForWWNKey.getPwwnKey(), refForWWNKey.getVolumeUri(), refForWWNKey.getId().toString()));
                                    recordZoneEvent(refForWWNKey, OperationTypeEnum.REMOVE_SAN_ZONE.name(),
                                            OperationTypeEnum.REMOVE_SAN_ZONE.getDescription());
                                }
  
                            }
                        }
                    } catch (DatabaseException ex) {
                        _log.error("Could not persist FCZoneReference: " + refKey);
                    }
                }

            } else { /* Adding zones */
                result = networkDevice.addZones(device, zones, fabricId, fabricWwn, true);
                if (result.isCommandSuccess()) {
                    String refKey = null;
                    try {
                        for (NetworkFCZoneInfo fabricInfo : fabricInfos) {
                            String[] newOrExisting = new String[1];
                            FCZoneReference ref = addZoneReference(exportGroupUri, fabricInfo, newOrExisting);
                            fabricInfo.setFcZoneReferenceId(ref.getId());  // this is needed for rollback
                            _log.info(String.format(
                                    "%s FCZoneReference key: %s volume %s group %s",
                                    newOrExisting[0], ref.getPwwnKey(), ref.getVolumeUri(), exportGroupUri));
                            recordZoneEvent(ref, OperationTypeEnum.ADD_SAN_ZONE.name(),
                                    OperationTypeEnum.ADD_SAN_ZONE.getDescription());
                        }
                    } catch (DatabaseException ex) {
                        _log.error("Could not persist FCZoneReference: " + refKey);
                    }
                }
            }
            // Update the FCZoneInfo structures if we changed device state for rollback.
            Map<String, String> map = (Map<String, String>) result.getObjectList().get(0);
            for (NetworkFCZoneInfo info : fabricInfos) {
                if (NetworkSystemDevice.SUCCESS.equals(map.get(info.getZoneName()))) {
                    info.setCanBeRolledBack(true);
                }
            }
            if (!result.isCommandSuccess()) {
                ServiceError serviceError = NetworkDeviceControllerException.errors.addRemoveZonesFailed(
                        device.getSystemType());
                setStatus(ExportGroup.class, exportGroupUri, taskId, false, serviceError);
            } else {
                setStatus(ExportGroup.class, exportGroupUri, taskId, true, null);
            }
            return result;
        } catch (ControllerException ex) {
            NetworkFCZoneInfo fabricInfo = fabricInfos.get(0);
            URI primaryUri = fabricInfo.getNetworkDeviceId();
            URI altUri = fabricInfo.getAltNetworkDeviceId();
            // If we took an error, attempt a retry with an alternate device if possible.
            if (altUri != null && retryAltNetworkDevice) {
                NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
                fabricLock = null;
                _log.error("Zone operation failed using device: " + primaryUri + " retrying with alternate device: " + altUri);
                fabricInfo.setNetworkDeviceId(altUri);
                device = getDeviceObject(altUri);
                return addRemoveZones(device, fabricId, fabricWwn, exportGroupUri, fabricInfos, doRemove, false);
            } else {
                if (result != null) {
                    if (!result.isCommandSuccess()) {
                        ServiceError serviceError = NetworkDeviceControllerException.errors.addRemoveZonesFailed(
                                device.getSystemType());
                        setStatus(ExportGroup.class, exportGroupUri, taskId, false, serviceError);
                    } else {
                        setStatus(ExportGroup.class, exportGroupUri, taskId, true, null);
                    }
                }
                throw ex;
            }
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    /**
     * Adds/removes a bunch of zones based on their NetworkFCZoneInfo structures.
     * They are split into groups and subgroups, first by the device used for zoning, and then by the fabricId to be zoned.
     * Then each subgroup is processed separately.
     * 
     * @param exportGroupUri
     * @param fabricInfos
     * @return BiosCommandResult
     * @throws ControllerException
     */
    public BiosCommandResult addRemoveZones(URI exportGroupUri, List<NetworkFCZoneInfo> fabricInfos, boolean doRemove)
            throws ControllerException {
        // Group the fabric infos together based on which devices should zone them.
        Map<URI, NetworkSystem> deviceId2NetworkSystem = new HashMap<URI, NetworkSystem>();
        Map<URI, List<NetworkFCZoneInfo>> deviceId2NetworkFabricInfos = new HashMap<URI, List<NetworkFCZoneInfo>>();
        for (NetworkFCZoneInfo fabricInfo : fabricInfos) {
            URI deviceId = fabricInfo.getNetworkDeviceId();
            URI altDeviceId = fabricInfo.getAltNetworkDeviceId();
            NetworkSystem device = null;

            // Determine device. The device structures are cached in deviceId2NetworkSystem
            device = deviceId2NetworkSystem.get(deviceId);
            if (device == null) {
                device = getDeviceObject(deviceId);
                if (device != null && device.getInactive() == false) {
                    deviceId2NetworkSystem.put(deviceId, device);
                } else if (altDeviceId != null) {
                    device = deviceId2NetworkSystem.get(altDeviceId);
                    if (device == null) {
                        device = getDeviceObject(altDeviceId);
                        if (device != null && device.getInactive() == false) {
                            deviceId2NetworkSystem.put(altDeviceId, device);
                        }
                    }
                }
            }
            if (device == null) {
                throw NetworkDeviceControllerException.exceptions.addRemoveZonesFailedNoDev(deviceId.toString());
            }

            List<NetworkFCZoneInfo> finfos = deviceId2NetworkFabricInfos.get(device.getId());
            if (finfos == null) {
                finfos = new ArrayList<NetworkFCZoneInfo>();
                deviceId2NetworkFabricInfos.put(device.getId(), finfos);
            }
            finfos.add(fabricInfo);
        }

        // Now loop through each device, splitting the collection of fabric infos by fabric ID/WWN.
        StringBuilder messageBuffer = new StringBuilder();
        for (URI deviceId : deviceId2NetworkFabricInfos.keySet()) {
            NetworkSystem device = deviceId2NetworkSystem.get(deviceId);
            Map<String, List<NetworkFCZoneInfo>> fabric2FabricInfos = new HashMap<String, List<NetworkFCZoneInfo>>();
            Map<String, NetworkLite> fabricId2Network = new HashMap<String, NetworkLite>();
            List<NetworkFCZoneInfo> finfos = deviceId2NetworkFabricInfos.get(deviceId);
            for (NetworkFCZoneInfo fabricInfo : finfos) {
                String fabricId = fabricInfo.getFabricId();
                String fabricWwn = fabricInfo.getFabricWwn();
                String key = (fabricWwn != null) ? fabricWwn : fabricId;
                updateAltDeviceid(fabricInfo, fabricId, fabricWwn, key, fabricId2Network);
                List<NetworkFCZoneInfo> singleFabricInfos = fabric2FabricInfos.get(key);
                if (singleFabricInfos == null) {
                    singleFabricInfos = new ArrayList<NetworkFCZoneInfo>();
                    fabric2FabricInfos.put(key, singleFabricInfos);
                }
                singleFabricInfos.add(fabricInfo);
            }

            // Now for each fabric, do the zoning.
            for (String key : fabric2FabricInfos.keySet()) {
                List<NetworkFCZoneInfo> singleFabricInfos = fabric2FabricInfos.get(key);
                String fabricId = singleFabricInfos.get(0).getFabricId();
                String fabricWwn = singleFabricInfos.get(0).getFabricWwn();
                BiosCommandResult rslt = addRemoveZones(device, fabricId, fabricWwn, exportGroupUri, singleFabricInfos, doRemove, true);
                if (messageBuffer.length() > 0) {
                    messageBuffer.append("; ");
                }
                messageBuffer.append(rslt.getMessage());
            }
        }
        BiosCommandResult result = BiosCommandResult.createSuccessfulResult();
        return result;
    }

    /**
     * Ensures every fabricInfo has its altNetworkDevice that is not null when
     * the calling code did not already supply a value for this field.
     * 
     * @param fabricInfo the fabric info to be updated
     * @param fabricId the fabric id of the network where zoning will be performed
     * @param fabricWWN the WWN of the network where zoning will be performed
     * @param key the key used to save already retrieved networks in the map
     *            this key is the fabric WWN unless it is null, then it is the fabric id
     * @param fabricId2Network a map where retrieved networks are saved between calls
     *            into this function. This is done to avoid repeated db retrieves of same objects
     */
    private void updateAltDeviceid(NetworkFCZoneInfo fabricInfo, String fabricId, String fabricWWN,
            String key, Map<String, NetworkLite> fabricId2Network) {
        if (fabricInfo != null && fabricInfo.getAltNetworkDeviceId() == null) {
            if (fabricId2Network.get(key) == null) {
                NetworkLite network = NetworkUtil.getNetworkLiteByFabricId(fabricId, fabricWWN, _dbClient);
                if (network != null) {
                    fabricId2Network.put(key, network);
                    URI id = fabricInfo.getNetworkDeviceId();
                    for (String strUri : network.getNetworkSystems()) {
                        if (!strUri.equals(id.toString())) {
                            id = URI.create(strUri);
                            break;
                        }
                    }
                    fabricInfo.setAltNetworkDeviceId(id);
                }
            }
        }
    }

    @Override
    public void removeSanZones(URI uri, String fabricId, String fabricWwn, List<Zone> zones, boolean activateZones,
            String taskId) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.removeSanZonesFailedNull(
                        device.getSystemType());
            }
            BiosCommandResult result = networkDevice.removeZones(device, zones, fabricId, fabricWwn, activateZones);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.REMOVE_SAN_ZONE, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.removeSanZonesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    /**
     * Remove a zone.
     * 
     * @param volUri URI of the Volume
     * @param fabricInfo NetworkFabricInfo generated by NetworkScheduler
     * @return BiosCommandResult
     */
    public BiosCommandResult removeZone(URI volUri, NetworkFCZoneInfo fabricInfo, boolean activateZones) throws ControllerException {
        ServiceError serviceError = NetworkDeviceControllerException.errors.zoningFailedArgs(
                volUri.toString());
        BiosCommandResult result = BiosCommandResult.createErrorResult(serviceError);
        List<Zone> zones = new ArrayList<Zone>();
        Zone zone = new Zone(fabricInfo.getZoneName());
        zones.add(zone);
        String taskId = UUID.randomUUID().toString();
        for (String address : fabricInfo.getEndPoints()) {
            ZoneMember member = new ZoneMember(address, ConnectivityMemberType.WWPN);
            zone.getMembers().add(member);
        }
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricInfo.getFabricId(), _coordinator);
        try {
            NetworkSystem device = getDeviceObject(fabricInfo.getNetworkDeviceId());
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.removeZoneFailedNull(device.getSystemType());
            }
            if (fabricInfo.isLastReference() == true && !fabricInfo.isExistingZone()) {
                result = networkDevice.removeZones(device, zones, fabricInfo.getFabricId(), fabricInfo.getFabricWwn(), activateZones);
            } else {
                // This is not the last reference, just mark our FCZoneReference for deletion
                result = BiosCommandResult.createSuccessfulResult();
            }
            if (result.isCommandSuccess()) {
                if (fabricInfo.getFcZoneReferenceId() != null) {
                    try {		// Mark our FcZoneReference object for removal
                        FCZoneReference reference = _dbClient.queryObject(FCZoneReference.class, fabricInfo.getFcZoneReferenceId());
                        if (reference != null) {
                            _dbClient.markForDeletion(reference);
                            recordZoneEvent(reference, OperationTypeEnum.REMOVE_SAN_ZONE.name(),
                                    OperationTypeEnum.REMOVE_SAN_ZONE.getDescription());
                        }
                    } catch (Exception ex) {
                        _log.error("Can't mark object for removal: " + fabricInfo.getFcZoneReferenceId());
                    }
                }
            }
            if (!result.isCommandSuccess()) {
                ServiceError svcError = NetworkDeviceControllerException.errors.removeZoneFailed(
                        volUri.toString(), device.getSystemType());
                setStatus(Volume.class, volUri, taskId, false, svcError);
            } else {
                setStatus(Volume.class, volUri, taskId, true, null);
            }
        } catch (ControllerException ex) {
            URI primaryUri = fabricInfo.getNetworkDeviceId();
            URI altUri = fabricInfo.getAltNetworkDeviceId();
            if (altUri != null && altUri != primaryUri) {
                NetworkFabricLocker.unlockFabric(fabricInfo.getFabricId(), fabricLock);
                fabricLock = null;
                _log.error("Remove Zone failed using device: " + primaryUri + " retrying with alternate device: " + altUri);
                fabricInfo.setNetworkDeviceId(altUri);
                return removeZone(volUri, fabricInfo, activateZones);
            } else {
                ServiceError svcError = NetworkDeviceControllerException.errors.removeZoneFailedExc(
                        volUri.toString());
                setStatus(Volume.class, volUri, taskId, false, svcError);
                throw ex;
            }
        } finally {
            NetworkFabricLocker.unlockFabric(fabricInfo.getFabricId(), fabricLock);
        }
        return result;
    }

    @Override
    public void updateSanZones(URI uri, String fabricId, String fabricWwn, List<ZoneUpdate> zones, boolean activateZones,
            String taskId) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.updateSanZonesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.updateZones(device, zones, fabricId, fabricWwn, activateZones);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.UPDATE_SAN_ZONE, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.updateSanZonesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    @Override
    public void activateSanZones(URI uri, String fabricId, String fabricWwn, String taskId) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.updateSanZonesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.activateZones(device, fabricId, fabricWwn);
            ServiceError serviceError = NetworkDeviceControllerException.errors.activateSanZonesFailed(
                    uri.toString(), device.getSystemType());
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), serviceError);

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.ACTIVATE_SAN_ZONE, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.activateSanZonesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    private void setStatus(Class clz, URI uri, String taskId, boolean success, ServiceCoded serviceCode) {
        try {
            if (success) {
                _dbClient.ready(clz, uri, taskId);
            } else {
                _dbClient.error(clz, uri, taskId, serviceCode);
            }
        } catch (Exception ex) {
            _log.error("Exception trying to setStatus: " + ex.getLocalizedMessage());
        }
    }

    /**
     * Make a String key from two URIs.
     * 
     * @param uri1
     * @param uri2
     * @return
     */
    private String make2UriKey(URI uri1, URI uri2) {
        String part1 = "null";
        String part2 = "null";
        if (uri1 != null) {
            part1 = uri1.toString();
        }
        if (uri2 != null) {
            part2 = uri2.toString();
        }
        return part1 + "+" + part2;
    }

    @Override
    public void deleteNetworkSystem(URI network, String taskId)
            throws ControllerException {
        try {
            NetworkSystem networkDevice = getDeviceObject(network);
            URIQueryResultList epUriList = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getNetworkSystemFCPortConnectionConstraint(network), epUriList);
            while (epUriList.iterator().hasNext()) {
                FCEndpoint connection = _dbClient.queryObject(FCEndpoint.class, epUriList.iterator().next());
                if (connection != null) {
                    _dbClient.removeObject(connection);
                }
            }
            List<URI> tzUriList = _dbClient.queryByType(Network.class, true);
            NetworkDiscoveryWorker worker =
                    new NetworkDiscoveryWorker(
                            getDevice(networkDevice.getSystemType()), _dbClient);
            worker.setCoordinator(_coordinator);
            for (URI tzUri : tzUriList) {
                Network tz = _dbClient.queryObject(Network.class, tzUri);
                if (tz != null && (tz.getNetworkSystems() != null
                        && tz.getNetworkSystems().contains(network.toString()))) {
                    worker.removeNetworkSystemTransportZone(tz, network.toString());
                }
            }
            if (taskId != null) {
                _dbClient.ready(NetworkSystem.class, network, taskId);
                _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                        OperationTypeEnum.DELETE_NETWORK_SYSTEM, System.currentTimeMillis(),
                        AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                        networkDevice.getId().toString(), networkDevice.getLabel(), networkDevice.getPortNumber(),
                        networkDevice.getUsername(), networkDevice.getSmisProviderIP(), networkDevice.getSmisPortNumber(),
                        networkDevice.getSmisUserName(), networkDevice.getSmisUseSSL());
            }
        } catch (Exception ex) {
            String msg = MessageFormat.format("Exception encountered while removing FC Port Connection for {0} because: {1}", network,
                    ex.getLocalizedMessage());
            _log.error(msg);
            if (taskId != null) {
                try {
                    ServiceError serviceError = NetworkDeviceControllerException.errors.deleteNetworkSystemFailed(
                            network.toString(), ex);
                    _dbClient.error(NetworkSystem.class, network, taskId, serviceError);
                } catch (DatabaseException e) {
                    _log.error(e.getMessage());
                }
            }
        }
    }

    /**
     * Returns true if zoning is required for the given VirtualArray
     * 
     * @param varrayURI - The VirtualArray URI.
     */
    private boolean isZoningRequired(URI varrayURI) {
        VirtualArray virtualArray = _dbClient.queryObject(VirtualArray.class, varrayURI);
        if (virtualArray == null) {
            throw DeviceControllerException.exceptions.virtualArrayNotFound();
        }
        return NetworkScheduler.isZoningRequired(_dbClient, virtualArray);
    }

    /**
     * Sets the completed Workflow state to read or error depending on the
     * BiosCommandResult received from lower layers.
     * 
     * @param token String Workflow stepId
     * @param result BiosCommandResult
     */
    private void completeWorkflowState(String token, String operation, BiosCommandResult result) {
        // Update the workflow state.
        if (Operation.Status.valueOf(result.getCommandStatus()).equals(Operation.Status.ready)) {
            WorkflowStepCompleter.stepSucceded(token);
        } else if (Operation.Status.valueOf(result.getCommandStatus()).equals(Operation.Status.error)) {
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneOperationFailed(
                    operation, result.getMessage());
            WorkflowStepCompleter.stepFailed(token, svcError);
        }
    }

    /**
     * Returns true if zoning required; sets Workflow Step status to
     * executing or suceeded depending.
     * 
     * @param token - Workflow step id.
     * @param varrayURI URI of virtual array
     * @return true if zoning required
     */
    private boolean checkZoningRequired(String token, URI varrayURI) {
        if (!isZoningRequired(varrayURI)) {
            WorkflowStepCompleter.stepSucceded(token);
            return false;
        } else {
            WorkflowStepCompleter.stepExecuting(token);
            return true;
        }
    }

    // ===========================================================================================================
    // External Interfaces to BlockDeviceController
    // ===========================================================================================================

    /**
     * Creates a Workflow Method for creating zones for a list of ExportMasks.
     * 
     * @see zoneExportMasksCreate
     * @param exportGroupURI -- ExportGroup URI
     * @param exportMaskURIs -- A list of ExportMask URIs.
     * @param volumeURIs -- A Collection of ExportMask URIs.
     * @return - boolean true if successful, false if not
     */
    public Workflow.Method zoneExportMasksCreateMethod(
            URI exportGroupURI, List<URI> exportMaskURIs, Collection<URI> volumeURIs) {
        return new Workflow.Method("zoneExportMasksCreate", exportGroupURI, exportMaskURIs, volumeURIs);
    }

    /**
     * Creates zones for one or more newly created ExportMasks.
     * Each ExportMask is assumed to have a zoningMap that indicates which
     * initiators should be zoned to which ports.
     * This routine will create zones for each of the ExportMasks
     * according to their zoningMap. The zones will assume to be
     * used to access the Volumes in the indicated collection.
     * Note: these arguments (except token) must match zoneExportMasksCreateMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param URI exportGroupURI -- ExportGroup URI
     * @param exportMaskURIs -- A list of ExportMask URIs to be zoned.
     * @param volumeURIs -- A collection of Volume URIs to be zoned
     * @param token -- The workflow step id.
     * @return boolean true if succesful, false if not
     */
    public boolean zoneExportMasksCreate(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token) {
    	
    	
        ExportGroup exportGroup = null;
        List<URI> listExportGroupURI = new ArrayList<URI>();  
        listExportGroupURI.add(exportGroupURI);
        
       
        try {
        	
        	Iterator<ExportGroup> exportGroupIterator = _dbClient.queryIterativeObjects(ExportGroup.class, listExportGroupURI);
        	exportGroup = exportGroupIterator.next();
        	
        	
            _log.info(String.format("Entering zoneExportMasksCreate for ExportGroup: %s (%s)",
                    exportGroup.getLabel(), exportGroup.getId()));
            if (exportMaskURIs == null && exportGroup.getExportMasks() != null) {
                // If the ExportMasks aren't specified, do all in the ExportGroup.
                exportMaskURIs = new ArrayList<URI>(Collections2.transform(
                        exportGroup.getExportMasks(),
                        CommonTransformerFunctions.FCTN_STRING_TO_URI));
            }

            if (_log.isDebugEnabled()) {
                for (URI maskURI : exportMaskURIs) {
                    ExportMask mask = _dbClient.queryObject(ExportMask.class,
                            maskURI);
                    if (mask != null) {
                        _log.debug(String.format("ExportMask %s (%s) storage %s",
                                mask.getMaskName(), mask.getId(), mask.getStorageDevice()));
                    }
                }
            }
        } catch (Exception ex) {
            _log.error("Exception zoning Export Masks", ex);
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneExportGroupCreateFailed(
                    ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(token, svcError);
        }
        return doZoneExportMasksCreate(exportGroup, exportMaskURIs, volumeURIs, token, true);
    }

    /**
     * Handles ExportGroup / ExportMask create, as well as add volume
     * 
     * @param exportGroup
     * @param exportMaskURIs
     * @param volumeURIs
     * @param token
     * @param checkZones Flag to enable or disable zoning check on a Network System
     * @return
     */
    private boolean doZoneExportMasksCreate(ExportGroup exportGroup,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token, boolean checkZones) {
        BiosCommandResult result = null;
        NetworkFCContext context = new NetworkFCContext();
        try {
            if (!checkZoningRequired(token, exportGroup.getVirtualArray())) {
                return true;
            }
            volumeURIs = removeDuplicateURIs(volumeURIs);

            // In the case of export group create, we created this step before we even
            // knew which ExportMask we need to update. There's a chance we don't have
            // to update any at all, in which case we can success out.
            if (exportMaskURIs == null || exportMaskURIs.isEmpty()) {
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            // Compute the zones for the ExportGroup
            // [hala] make sure we do not rollback existing zones
            Map<String, List<Zone>> zonesMap = new HashMap<String, List<Zone>>();
            if (checkZones) {
                zonesMap = getExistingZonesMap(exportMaskURIs, token);
            }

            List<NetworkFCZoneInfo> zones = _networkScheduler.
                    getZoningTargetsForExportMasks(exportGroup, exportMaskURIs, volumeURIs, zonesMap, checkZones, _dbClient);
            context.getZoneInfos().addAll(zones);
            logZones(zones);

            // If there are no zones to do, we were successful.
            if (!checkZones) {
                if (!context.getZoneInfos().isEmpty()) {
                    String[] newOrExisting = new String[1];
                    for (NetworkFCZoneInfo zoneInfo : context.getZoneInfos()) {
                        addZoneReference(exportGroup.getId(), zoneInfo, newOrExisting);
                    }
                }
                result = BiosCommandResult.createSuccessfulResult();
            } else {
                // Now call addZones to add all the required zones.
                result = addRemoveZones(exportGroup.getId(),
                        context.getZoneInfos(), false);
            }

            // Save our zone infos in case we want to rollback.
            WorkflowService.getInstance().storeStepData(token, context);

            // Update the workflow state.
            completeWorkflowState(token, "zoneExportMaskCreate", result);

        } catch (Exception ex) {
            _log.error("Exception zoning Export Masks", ex);
            // Save our zone infos in case we want to rollback.
            WorkflowService.getInstance().storeStepData(token, context);
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneExportGroupCreateFailed(
                    ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(token, svcError);
        }
        return (result != null && result.isCommandSuccess());
    }

    /**
     * This function wraps the logic for getting zones from the network system. The zones may have already
     * been read in previous workflow steps during port assignment. To avoid another call into the network
     * systems, these zones are stored in the workflow. In the case of "add initiators" workflow, only the
     * new initiators's zones are read by port allocation. In this case, the new initiators zones are loaded
     * from the workflow while the old initiators zones are retrieved from the network system.
     * 
     * @param exportMaskUris -- the URI of the export mask being zones
     * @param token -- the workflow step id
     * @return a map of initiatorPort to the list if zones that already exist on the network system
     */
    private Map<String, List<Zone>> getExistingZonesMap(Collection<URI> exportMaskUris, String token) {

        // get existing zones from the switch, first check if the zones were retrieved by previous steps and cached in the workflow
        Map<String, List<Zone>> zonesMap = (Map<String, List<Zone>>) WorkflowService.getInstance().loadWorkflowData(token, "zonemap");

        // if the existing zones were not already retrieved and cached by other steps, retrieve them now
        if (zonesMap == null) {
            zonesMap = new HashMap<String, List<Zone>>();
        } else {
            _log.info("Existing zones were found in the workflow for initiators {}", zonesMap.keySet());
        }
        List<Initiator> exportInitiators = ExportUtils.getExportMasksInitiators(exportMaskUris, _dbClient);
        Iterator<Initiator> itr = exportInitiators.iterator();
        Initiator ini = null;
        List<String> otherInitiators = new ArrayList<String>();
        while (itr.hasNext()) {
            ini = itr.next();
            if (zonesMap.containsKey(ini.getInitiatorPort())) {
                itr.remove();
            } else {
                otherInitiators.add(ini.getInitiatorPort());
            }
        }
        if (!exportInitiators.isEmpty()) {
            _log.info("Getting existing zones from network system for {} ", otherInitiators);
            zonesMap.putAll(getInitiatorsZones(exportInitiators));
        }
        return zonesMap;

    }

    /**
     * Get the Workflow.Method for zoneExportAddVolumesMethod.
     * 
     * @param exportGroupURI
     * @param exportMaskURIs
     * @param volumeURIs
     * @return Workflow.Method
     */
    public Workflow.Method zoneExportAddVolumesMethod(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs) {
        return new Workflow.Method("zoneExportAddVolumes", exportGroupURI, exportMaskURIs, volumeURIs);
    }

    /**
     * Called when volumes are added to ExportMasks. This is important because we
     * need to add FCZoneReferences for each of the new volumes so that in the
     * future a zone will not be deleted until all volumes using the zone have
     * been removed. References will only be added for zones with ports belonging
     * to the array containing the volume.
     * Note: these arguments (except token) must match zoneExportAddVolumesMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param exportGroupURI -- ExportGroup URI
     * @param exportMaskURIs -- List of Export Mask URIs receiving the Volumes
     * @param volumeURIs -- Collection of Volume URIs
     * @param token -- Step ID
     * @return
     */
    public boolean zoneExportAddVolumes(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token) {
        ExportGroup exportGroup = _dbClient
                .queryObject(ExportGroup.class, exportGroupURI);
        _log.info(String.format
                ("Entering zoneExportAddVolumes for ExportGroup: %s (%s) Volumes: %s",
                        exportGroup.getLabel(), exportGroup.getId(), volumeURIs.toString()));
        // Check if Zoning needs to be checked from system config
        // call the doZoneExportMasksCreate to check/create/remove zones with the flag
        String addZoneWhileAddingVolume = customConfigHandler.getComputedCustomConfigValue(
                CustomConfigConstants.ZONE_ADD_VOLUME,
                CustomConfigConstants.GLOBAL_KEY, null);
        // Default behavior is we allow zoning checks against the Network System
        Boolean addZoneOnDeviceOperation = true;
        _log.info("zoneExportAddVolumes checking for custom config value {} to skip zoning checks : (Default) : {}",
                addZoneWhileAddingVolume, addZoneOnDeviceOperation);
        if (addZoneWhileAddingVolume != null) {
            addZoneOnDeviceOperation = Boolean.valueOf(addZoneWhileAddingVolume);
            _log.info("Boolean convereted of : {} : returned by Config handler as : {} ",
                    addZoneWhileAddingVolume, addZoneOnDeviceOperation);
        } else {
            _log.info("Config handler returned null for value so going by default value {}", addZoneOnDeviceOperation);
        }

        _log.info("zoneExportAddVolumes checking for custom config value {} to skip zoning checks : (Custom Config) : {}",
                addZoneWhileAddingVolume, addZoneOnDeviceOperation);

        return doZoneExportMasksCreate(exportGroup, exportMaskURIs, volumeURIs, token,
                addZoneOnDeviceOperation);
    }

    /**
     * Adds a specified list of Initiators to each of the specified ExportMasks.
     * 
     * @param exportGroupURI
     * @param exportMasksToInitiators - Map of ExportMap URI to list of Initiator URIs
     * @param exportGroup
     * @param exportMasksToInitiators
     * @return
     */
    public Workflow.Method zoneExportAddInitiatorsMethod(URI exportGroupURI,
            Map<URI, List<URI>> exportMasksToInitiators) {
        return new Workflow.Method("zoneExportAddInitiators", exportGroupURI, exportMasksToInitiators);
    }

    /**
     * Handles zoning for ExportGroup.exportAddInitiator() call.
     * This call adds zones for each of the initiators in the exportMasksToInitiators map.
     * The ports are determined from the ExportMask:
     * 1) if the ExportMask.zoningMap has an entry for an initiator, the ports are taken
     * from there, otherwise
     * 2) the ports are taken from ExportMask.storagePorts.
     * Note: these arguments (except token) must match zoneExportAddInitiatorsMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param exportGroup -- Used for the zone references.
     * @param exportMasksToInitiators - Map of ExportMap URI to list of Initiator URIs
     * @param token Workflow step id
     * @return true if success, false otherwise
     * @throws ControllerException
     */
    public boolean zoneExportAddInitiators(URI exportGroupURI,
            Map<URI, List<URI>> exportMasksToInitiators,
            String token) throws ControllerException {
        NetworkFCContext context = new NetworkFCContext();
        boolean status = false;
        ExportGroup exportGroup = _dbClient
                .queryObject(ExportGroup.class, exportGroupURI);
        _log.info(String.format("Entering zoneExportAddInitiators for ExportGroup: %s (%s)",
                exportGroup.getLabel(), exportGroup.getId()));
        try {
            if (!checkZoningRequired(token, exportGroup.getVirtualArray())) {
                return true;
            }

            // get existing zones on the switch
            Map<String, List<Zone>> zonesMap = getExistingZonesMap(exportMasksToInitiators.keySet(), token);

            // Compute zones that are required.
            List<NetworkFCZoneInfo> zoneInfos =
                    _networkScheduler.getZoningTargetsForInitiators(exportGroup, exportMasksToInitiators, zonesMap, _dbClient);
            context.getZoneInfos().addAll(zoneInfos);
            logZones(zoneInfos);

            // If there are no zones to do, we were successful.
            if (context.getZoneInfos().isEmpty()) {
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            // Now call addZones to add all the required zones.
            BiosCommandResult result = addRemoveZones(exportGroup.getId(),
                    context.getZoneInfos(), false);
            status = result.isCommandSuccess();
            // Save our zone infos in case we want to rollback.
            WorkflowService.getInstance().storeStepData(token, context);

            // Update the workflow state.
            completeWorkflowState(token, "zoneExportAddInitiators", result);

            return status;
        } catch (Exception ex) {
            _log.error("Exception zoning add initiators", ex);
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneExportAddInitiatorsFailed(
                    ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(token, svcError);
            return status;
        }
    }

    /**
     * Returns a Workflow Method to delete ExportMask(s) from an ExportGroup
     * 
     * @param exportGroupURI
     * @param exportMaskURIs
     * @param volumeURIs
     * @return
     */
    public Workflow.Method zoneExportMasksDeleteMethod(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs) {
        return new Workflow.Method("zoneExportMasksDelete",
                exportGroupURI, exportMaskURIs, volumeURIs);
    }

    /**
     * Workflow step to do zone removal when deleting one or more ExportMasks from an ExportGroup.
     * This will delete all the zones from initiators in the export mask to volumes in the
     * export mask if all the volumes in the export mask are enclosed in the collection.
     * Note: these arguments (except token) must match zoneExportMasksDeleteMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param exportGroupURI
     * @param exportMaskURIs
     * @param volumeURIs
     * @param token
     * @return true if successful
     */
    public boolean zoneExportMasksDelete(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token) {
        ExportGroup exportGroup = _dbClient
                .queryObject(ExportGroup.class, exportGroupURI);
        _log.info(String.format("Entering zoneExportMasksDelete for ExportGroup: %s (%s)",
                exportGroup.getLabel(), exportGroup.getId()));
        return doZoneExportMasksDelete(exportGroup, exportMaskURIs, volumeURIs, token);
    }

    /**
     * Remove ExportMasks or delete Volumes from ExportMasks.
     * 
     * @param exportGroupURI -- the Export Group URI
     * @param exportMaskURIs -- List of Export Mask URIs
     * @param volumeURIs -- Collection of all volumes being removed
     * @param token - Step id.
     * @return
     */
    private boolean doZoneExportMasksDelete(ExportGroup exportGroup,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token) {
        NetworkFCContext context = new NetworkFCContext();
        boolean status = false;
        try {
            if (!checkZoningRequired(token, exportGroup.getVirtualArray())) {
                return true;
            }
            volumeURIs = removeDuplicateURIs(volumeURIs);

            // Compute the zones for the ExportGroup
            context.setAddingZones(false);
            List<NetworkFCZoneInfo> zones = _networkScheduler.
                    getRemoveZoningTargetsForExportMasks(exportGroup, exportMaskURIs, volumeURIs);
            context.getZoneInfos().addAll(zones);
            logZones(zones);

            // If there are no zones to do, we were successful.
            if (context.getZoneInfos().isEmpty()) {
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            List<NetworkFCZoneInfo> lastReferenceZoneInfo = new ArrayList<NetworkFCZoneInfo>();
            for (NetworkFCZoneInfo zoneInfo : zones) {
                if (zoneInfo.isLastReference()) {
                    _log.debug("EndPoints with last reference" + zoneInfo.getEndPoints());
                    lastReferenceZoneInfo.add(zoneInfo);
                }
            }
            // Now call removeZones to remove all the required zones.
            BiosCommandResult result = addRemoveZones(exportGroup.getId(), context.getZoneInfos(), true);
            status = result.isCommandSuccess();

            if (status && !lastReferenceZoneInfo.isEmpty()) {
                _log.info("There seems to be last reference zones that were removed, clean those zones from the zoning map.");
                updateZoningMap(lastReferenceZoneInfo, exportGroup.getId(), exportMaskURIs);
            }
            // Update the workflow state.
            completeWorkflowState(token, "zoneExportMasksDelete", result);

            _log.info("Successfully removed zones for ExportGroup: {}", exportGroup.toString());
        } catch (Exception ex) {
            _log.error("Exception zoning delete Export Masks", ex);
            // TODO revisit Exceptions
            // Save our zone infos in case we want to rollback.
            WorkflowService.getInstance().storeStepData(token, context);
            ServiceError svcError = NetworkDeviceControllerException.errors
                    .zoneExportGroupDeleteFailed(ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(token, svcError);
        }
        return status;
    }

    /**
     * This method updates zoning map. This will mostly be called when volume(s) is/are removed
     * from the ExportMask which is shared across two/more varrays and varrays do not have same
     * storage ports which results in creating zoning based on the ports in the varray.
     * 
     * lastReferenceZoneInfo contains the zones that were removed from the device,
     * according to this if there is initiator in the zoningMap with just one storage port
     * for which zone is removed then that entry is removed from the zoningMap.
     * If initiator has more than one storage port in the zoningMap for the initiator then
     * only storage port for which zone is removed is removed from the zoning map.
     * ExportMasks with ImmutableZoningMap set are skipped.
     * 
     * @param lastReferenceZoneInfo list of NetworkFCZoneInfo for the zones that are removed.
     * @param exportGroupURI reference to exportGroup
     * @param exportMaskURIs list of reference to exportMask
     */
    private void updateZoningMap(List<NetworkFCZoneInfo> lastReferenceZoneInfo, URI exportGroupURI, List<URI> exportMaskURIs) {

        if (exportMaskURIs == null || exportMaskURIs.isEmpty()) {
            ExportGroup exportGroup = _dbClient.queryObject(ExportGroup.class, exportGroupURI);
            if (exportGroup != null && exportGroup.getExportMasks() != null) {
                exportMaskURIs = new ArrayList<URI>();
                exportMaskURIs = StringSetUtil.stringSetToUriList(exportGroup.getExportMasks());
            }
        }

        for (URI exportMaskURI : exportMaskURIs) {
            ExportMask exportMask = _dbClient.queryObject(ExportMask.class, exportMaskURI);
            if (exportMask != null && !exportMask.getInactive()
                    && !exportMask.fetchDeviceDataMapEntry(
                            ExportMask.DeviceDataMapKeys.ImmutableZoningMap.name()).contains(Boolean.TRUE.toString())) {
                for (NetworkFCZoneInfo zoneInfo : lastReferenceZoneInfo) {
                    StringSetMap existingZoningMap = exportMask.getZoningMap();
                    if (exportMask.getVolumes() == null) {
                        continue;
                    }
                    Set<String> exportMaskVolumes = exportMask.getVolumes().keySet();
                    if (existingZoningMap != null
                            && zoneInfo.getVolumeId() != null
                            && exportMaskVolumes.contains(zoneInfo.getVolumeId().toString())
                            && zoneInfo.getEndPoints().size() == 2) {

                        Initiator initiator = NetworkUtil.findInitiatorInDB(zoneInfo.getEndPoints().get(0), _dbClient);
                        List<StoragePort> storagePorts = NetworkUtil.findStoragePortsInDB(zoneInfo.getEndPoints().get(1), _dbClient);

                        for (StoragePort storagePort : storagePorts) {
                            if (initiator != null && storagePort != null) {
                                for (String initiatorId : existingZoningMap.keySet()) {
                                    if (initiator.getId().toString().equals(initiatorId)) {
                                        StringSet ports = existingZoningMap.get(initiatorId);
                                        if (ports != null) {
                                            if (ports.contains(storagePort.getId().toString())) {
                                                ports.remove(storagePort.getId().toString());
                                                if (ports.isEmpty()) {
                                                    exportMask.removeZoningMapEntry(initiatorId);
                                                    _log.info("Removing zoning map entry for initiator {}, in exportmask {}",
                                                            initiatorId, exportMaskURI);
                                                } else {
                                                    exportMask.addZoningMapEntry(initiatorId, ports);
                                                    _log.info("Removing storagePort " + storagePort.getId()
                                                            + " from zoning map for initiator " + initiatorId
                                                            + " in export mask " + exportMaskURI);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    _dbClient.persistObject(exportMask);
                }
            }
        }
    }

    /**
     * Returns a Workflow Method for zoneExportRemoveVolumes
     * 
     * @param exportGroupURI -- Export Group URI
     * @param exportMaskURIs -- List of Export Mask URIs
     * @param volumeURIs -- Collection of Volume URIs
     * @return Workflow.Method
     */
    public Workflow.Method zoneExportRemoveVolumesMethod(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs) {
        return new Workflow.Method("zoneExportRemoveVolumes", exportGroupURI, exportMaskURIs, volumeURIs);
    }

    /**
     * This is called when volumes are removed from ExportMask(s). It is important because
     * we keep a FCZoneReference for each export group / volume pair to each zone.
     * The ExportMasks are read to determined the potential zones (initiators and
     * ports). If the call includes the last volume to use a zone, it will be
     * deleted.
     * Note: these arguments (except token) must match zoneExportRemoveVolumesMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param exportGroup
     * @param exportMaskURIs - a List of ExportMask URIs
     * @param volumeURIs - a Collection of Volume URIs
     * @param token - The Workflow Step id.
     * @return true if success, false otherwise
     * @throws IOException
     * @throws ControllerException
     */
    public boolean zoneExportRemoveVolumes(URI exportGroupURI,
            List<URI> exportMaskURIs, Collection<URI> volumeURIs, String token) {
        ExportGroup exportGroup = _dbClient
                .queryObject(ExportGroup.class, exportGroupURI);
        _log.info(String.format(
                "Entering zoneExportRemoveVolumes for ExportGroup: %s (%s) Volumes: %s",
                exportGroup.getLabel(), exportGroup.getId(), volumeURIs.toString()));
        return doZoneExportMasksDelete(exportGroup, exportMaskURIs, volumeURIs, token);
    }

    /**
     * Generate Workflow.Method for zoneExportRemoveInitiators
     * 
     * @param exportGroupURI
     * @param exportMasksToInitiators
     * @return
     */
    public Workflow.Method zoneExportRemoveInitiatorsMethod(
            URI exportGroupURI, Map<URI, List<URI>> exportMasksToInitiators) {
        return new Workflow.Method("zoneExportRemoveInitiators", exportGroupURI, exportMasksToInitiators);
    }

    /**
     * This will remove zones for all the initiators in the exportMasksToInitiators map.
     * The ExportMasks are read to determine the volumes that are using the zones, and
     * the potential ports that are paired to make up the zones.
     * Note: these arguments (except token) must match zoneExportRemoveInitiatorsMethod above.
     * This routine executes as a Workflow Step.
     * 
     * @param initiators
     * @param exportGroup
     * @param exportMask
     * @param context
     * @param completer TaskCompleter for the storage task in case of exception
     * @return true if success, false otherwise
     * @throws IOException
     * @throws ControllerException
     */
    public boolean zoneExportRemoveInitiators(URI exportGroupURI,
            Map<URI, List<URI>> exportMasksToInitiators,
            String token) throws ControllerException {
        NetworkFCContext context = new NetworkFCContext();
        boolean status = false;
        ExportGroup exportGroup = _dbClient
                .queryObject(ExportGroup.class, exportGroupURI);
        _log.info(String.format("Entering zoneExportRemoveInitiators for ExportGroup: %s (%s)",
                exportGroup.getLabel(), exportGroup.getId()));
        try {
            if (!checkZoningRequired(token, exportGroup.getVirtualArray())) {
                return true;
            }
            context.setAddingZones(false);

            // Get the zoning targets to be removed.
            List<NetworkFCZoneInfo> zoneInfos = _networkScheduler
                    .getRemoveZoningTargetsForInitiators(exportGroup, exportMasksToInitiators);
            context.getZoneInfos().addAll(zoneInfos);
            logZones(zoneInfos);

            // If there are no zones to do, we were successful.
            if (context.getZoneInfos().isEmpty()) {
                WorkflowStepCompleter.stepSucceded(token);
                return true;
            }

            // Now call removeZones to remove all the required zones.
            BiosCommandResult result = addRemoveZones(exportGroup.getId(), context.getZoneInfos(), true);
            status = result.isCommandSuccess();

            // Update the workflow state.
            completeWorkflowState(token, "zoneExportRemoveInitiators", result);

            // If the result is success, remove the initiators from the ExportMask zoningMap.
            if (result.isCommandSuccess()) {
                removeInitiatorsFromZoningMap(exportMasksToInitiators);
            }
            return status;

        } catch (Exception ex) {
            _log.error("Exception zoning remove initiators", ex);
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneExportRemoveInitiatorsFailed(
                    ex.getMessage(), ex);
            WorkflowStepCompleter.stepFailed(token, svcError);
            return status;
        }
    }

    /**
     * Returns the Workflow.Method for a zoneRollback operation.
     * 
     * @param exportGroupURI - ExportGroup URI
     * @param contextKey -- The context key which indicates what zones were configured on the device.
     *            This is the Step id of the zoning step.
     * @return Workflow.Method
     */
    public Workflow.Method zoneRollbackMethod(URI exportGroupURI, String contextKey) {
        return new Workflow.Method("zoneRollback", exportGroupURI, contextKey);
    }

    /**
     * Rollback any of the zoning operations.
     * 
     * @param exportGroupURI -- The ExportGroup URI
     * @param contextKey -- The context which indicates what zones were configured on the device.
     * @param taskId -- String tas identifier for WorkflowTaskCompleter.
     * @return
     * @throws DeviceControllerException
     */
    public boolean zoneRollback(URI exportGroupURI, String contextKey, String taskId) throws DeviceControllerException {
        try {
            NetworkFCContext context = (NetworkFCContext) WorkflowService.getInstance()
                    .loadStepData(contextKey);
            if (context == null) {
                _log.warn("No zone rollback information for Step: " + contextKey +
                        " , Export Group: " + exportGroupURI.toString() + ", and Task: " +
                        taskId + ". The zoning step either did not complete or encountered an error.");
                WorkflowStepCompleter.stepSucceded(taskId);
                return true;
            }
            logZones(context.getZoneInfos());
            WorkflowStepCompleter.stepExecuting(taskId);
            _log.info("Beginning zone rollback");
            // Determine what needs to be rolled back.
            List<NetworkFCZoneInfo> lastReferenceZoneInfo = new ArrayList<NetworkFCZoneInfo>();
            List<NetworkFCZoneInfo> rollbackList = new ArrayList<NetworkFCZoneInfo>();
            for (NetworkFCZoneInfo info : context.getZoneInfos()) {
                if (info.canBeRolledBack()) {
                    // If we were adding zones, we set lastReference so it will be deleted.
                    if (context.isAddingZones()) {
                        info.setLastReference(true);
                        lastReferenceZoneInfo.add(info);
                    }
                    rollbackList.add(info);
                }
            }

            BiosCommandResult result = addRemoveZones(exportGroupURI, rollbackList,
                    context.isAddingZones());

            completeWorkflowState(taskId, "ZoneRollback", result);

            if (result.isCommandSuccess() && !lastReferenceZoneInfo.isEmpty()) {
                _log.info("There seems to be last reference zones that were removed, clean those zones from the zoning map.");
                updateZoningMap(lastReferenceZoneInfo, exportGroupURI, null);
            }

            return result.isCommandSuccess();
        } catch (Exception ex) {
            ServiceError svcError = NetworkDeviceControllerException.errors.zoneRollbackFailedExc(
                    exportGroupURI.toString(), ex);
            WorkflowStepCompleter.stepFailed(taskId, svcError);
            return false;
        }
    }

    // ===========================================================================================================
    // end of External Interfaces to BlockDeviceController
    // ===========================================================================================================

    /**
     * Create a nice event based on the Zone
     * 
     * @param ref FCZoneReference for which the event is about
     * @param type Type of event such as modified, created, removed
     * @param description Description for the event if needed
     */
    private void recordZoneEvent(FCZoneReference ref, String type, String description) {
        if (ref == null) {
            _log.error("Invalid Zone event");
            return;
        }
        // TODO fix the bogus user ID once we have AuthZ working
        RecordableBourneEvent event = ControllerUtils.convertToRecordableBourneEvent(ref, type, description,
                null, _dbClient, EVENT_SERVICE_TYPE, RecordType.Event.name(), EVENT_SERVICE_SOURCE);

        try {
            _eventManager.recordEvents(event);
        } catch (Exception ex) {
            _log.error("Failed to record event. Event description: {}. Error:", description, ex);
        }
    }

    /**
     * Add a zone reference for an ExportGroup-Zone combination.
     * This method is careful not to duplicate existing FCZoneReferences matching the same
     * ExportGroup and Volume. Whether a new reference is persisted or not,
     * it returns the reference.
     * 
     * @param exportGroupURI -- the URI of the export group
     * @param zoneInfo -- the zoneInfo for which the FCZoneReference is being created
     * @param newOrExisting - OUT param in String[0] puts "New" or "Existing" indicating
     *            whether a New FCZoneReference was persisted.
     * @return an FCZoneReference for the zoneInfo-exportGroup combination
     */
    private FCZoneReference addZoneReference(URI exportGroupURI, NetworkFCZoneInfo zoneInfo, String[] newOrExisting) {
        String refKey = zoneInfo.makeEndpointsKey();
        FCZoneReference ref = addZoneReference(exportGroupURI, zoneInfo.getVolumeId(), refKey, zoneInfo.getFabricId(),
                zoneInfo.getNetworkDeviceId(), zoneInfo.getZoneName(), zoneInfo.isExistingZone(), newOrExisting);
        return ref;
    }

    /**
     * A base function for creating an FCZoneReference from the parameters. This function
     * ensures that duplicate FCZoneReference for the same refKey, volume and export group
     * is not created.
     * 
     * @param exportGroupURI -- the export group URI
     * @param volumeURI -- the volume URI
     * @param refKey -- the FCZoneReference key which is the concatenation of the initiator
     *            and storage port WWNs. Note that this key is formed by sorting the WWNs
     * @param fabricId -- the name of the fabric or the is of the vsan
     * @param NetworkSystemURI -- the network system used to add the zone
     * @param zoneName -- the zone name
     * @param existingZone -- an flag that indicates if the zone is created by the aplication
     *            or by the user, true means it was created by the user.
     * @param newOrExisting - OUT param in String[0] puts "New" or "Existing" indicating
     *            whether a New FCZoneReference was persisted.
     * @return The zone reference instance
     */
    private FCZoneReference addZoneReference(URI exportGroupURI, URI volumeURI,
            String refKey, String fabricId, URI NetworkSystemURI, String zoneName,
            boolean existingZone, String[] newOrExisting) {
        // Check to see that we don't add multiple references for same Volume/Export Group combination
        FCZoneReference ref = findFCZoneReferenceForVolGroupKey(exportGroupURI, volumeURI, refKey, newOrExisting);
        if (ref == null) {
            ref = new FCZoneReference();
            ref.setPwwnKey(refKey);
            ref.setFabricId(fabricId);
            ref.setNetworkSystemUri(NetworkSystemURI);
            ref.setVolumeUri(volumeURI);
            ref.setGroupUri(exportGroupURI);
            ref.setZoneName(zoneName);
            ref.setId(URIUtil.createId(FCZoneReference.class));
            ref.setInactive(false);
            ref.setLabel(FCZoneReference.makeLabel(ref.getPwwnKey(), volumeURI.toString()));
            ref.setExistingZone(existingZone);
            _dbClient.createObject(ref);
            newOrExisting[0] = "New";
        }
        return ref;
    }

    /**
     * Looks in the database for a zone for the same volume and export group and key
     * 
     * @param exportGroupURI -- the export group URI
     * @param volumeURI -- the volume URI
     * @param refKey -- the FCZoneReference key which is the concatenation of the initiator
     *            and storage port WWNs. Note that this key is formed by sorting the WWNs
     * @param newOrExisting - OUT param in String[0] puts "New" or "Existing" indicating
     *            whether a New FCZoneReference was persisted.
     * @return The zone reference instance if found, null otherwise
     */
    private FCZoneReference findFCZoneReferenceForVolGroupKey(URI exportGroupURI, URI volumeURI, String refKey, String[] newOrExisting) {
        Map<String, FCZoneReference> volRefMap = _networkScheduler.makeExportToReferenceMap(refKey);
        String volExportKey = make2UriKey(volumeURI, exportGroupURI);
        if (volRefMap.containsKey(volExportKey)) {
            FCZoneReference ref = volRefMap.get(volExportKey);
            // If we have an active reference, don't make another
            if (ref != null && ref.getInactive() == false) {
                _log.info(String.format("Existing zone reference: vol %s group %s refkey %s",
                        volumeURI, exportGroupURI, refKey));
                newOrExisting[0] = "Existing";
                return ref;
            }
        }
        return null;
    }

    private void logZones(List<NetworkFCZoneInfo> zones) {
        for (NetworkFCZoneInfo zone : zones) {
            _log.info(String.format("zone %s endpoints %s vol %s last %s ref %s existing %s",
                    zone.getZoneName(), zone.getEndPoints(), zone.getVolumeId(),
                    zone.isLastReference(), zone.getFcZoneReferenceId(), zone.isExistingZone()));
        }
    }

    /**
     * Removes duplicate URIs from a Collection.
     * 
     * @param uris
     * @return
     */
    private Collection<URI> removeDuplicateURIs(Collection<URI> uris) {
        HashSet<URI> set = new HashSet<URI>();
        set.addAll(uris);
        return set;
    }

    /**
     * Processes the initiators that were removed and removes them from the
     * ExportMask.zoningMap so the zoningMap will now be indicative of current state.
     * 
     * @param exportMasksToInitiators
     */
    private void removeInitiatorsFromZoningMap(Map<URI, List<URI>> exportMasksToInitiators) {
        for (URI maskURI : exportMasksToInitiators.keySet()) {
            ExportMask mask = _dbClient.queryObject(ExportMask.class, maskURI);
            if (mask == null || mask.getInactive()) {
                continue;
            }
            for (URI initiatorURI : exportMasksToInitiators.get(maskURI)) {
                if (mask.getZoningMap() != null) {
                    mask.removeZoningMapEntry(initiatorURI.toString());
                }
            }
            _dbClient.persistObject(mask);
        }
    }

    @Override
    public List<ZoneWwnAlias> getAliases(URI uri, String fabricId, String fabricWwn) throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Get the file device reference for the type of file device managed
        // by the controller.
        NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
        if (networkDevice == null) {
            throw NetworkDeviceControllerException.exceptions.getAliasesFailedNull(device.getSystemType());
        }
        try {
            List<ZoneWwnAlias> aliases = networkDevice.getAliases(device, fabricId, fabricWwn);
            return aliases;
        } catch (Exception ex) {
            Date date = new Date();
            throw NetworkDeviceControllerException.errors.getAliasesFailedExc(uri.toString(), date.toString(), ex);
        }
    }

    @Override
    public void addAliases(URI uri, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.addAliasesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.addAliases(device, aliases, fabricId, fabricWwn);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.ADD_ALIAS, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.addAliasesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    @Override
    public void removeAliases(URI uri, String fabricId, String fabricWwn, List<ZoneWwnAlias> aliases, String taskId)
            throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.removeAliasesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.removeAliases(device, aliases, fabricId, fabricWwn);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.REMOVE_ALIAS, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.removeAliasesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    @Override
    public void updateAliases(URI uri, String fabricId, String fabricWwn, List<ZoneWwnAliasUpdate> updateAliases, String taskId)
            throws ControllerException {
        NetworkSystem device = getDeviceObject(uri);
        // Lock to prevent concurrent operations on the same VSAN / FABRIC.
        InterProcessLock fabricLock = NetworkFabricLocker.lockFabric(fabricId, _coordinator);
        try {
            // Get the file device reference for the type of file device managed
            // by the controller.
            NetworkSystemDevice networkDevice = getDevice(device.getSystemType());
            if (networkDevice == null) {
                throw NetworkDeviceControllerException.exceptions.updateAliasesFailedNull(device.getSystemType());
            }
            BiosCommandResult result = networkDevice.updateAliases(device, updateAliases, fabricId, fabricWwn);
            setStatus(NetworkSystem.class, device.getId(), taskId, result.isCommandSuccess(), result.getServiceCoded());

            _auditMgr.recordAuditLog(null, null, EVENT_SERVICE_TYPE,
                    OperationTypeEnum.UPDATE_ALIAS, System.currentTimeMillis(),
                    AuditLogManager.AUDITLOG_SUCCESS, AuditLogManager.AUDITOP_END,
                    device.getId().toString(), device.getLabel(), device.getPortNumber(), device.getUsername(),
                    device.getSmisProviderIP(), device.getSmisPortNumber(), device.getSmisUserName(), device.getSmisUseSSL());
        } catch (Exception ex) {
            ServiceError serviceError = NetworkDeviceControllerException.errors.updateAliasesFailedExc(
                    device.getSystemType(), ex);
            _dbClient.error(NetworkSystem.class, device.getId(), taskId, serviceError);
        } finally {
            NetworkFabricLocker.unlockFabric(fabricId, fabricLock);
        }
    }

    /**
     * Given a list of initiators which are all in the network, for each
     * initiator find all the zones on the network system where the initiator
     * port WWN is a member. Returns the results as a map of zones grouped
     * by the initiator port WWN.
     * 
     * @param network the network of the initiators
     * @param initiators the initiators
     * @return a map of zones grouped by the initiator port WWN.
     */
    private Map<String, List<Zone>> getInitiatorsInNetworkZones(
            NetworkLite network, List<Initiator> initiators) {
        Map<String, List<Zone>> wwnToZones = new HashMap<String, List<Zone>>();
        fetchInitiatorsZones(network, initiators, wwnToZones);
        return wwnToZones;
    }

    /**
     * For the given network and initiators, which are in the network,
     * use one of the network's network system to get the zones and populate
     * wwnToZones map with the zones found. Return the network system
     * used to get the zones.
     * <p>
     * This function is created because to create the ZoneInfoMap of {@link #getInitiatorsInNetworkZoneInfoMap(NetworkLite, List, Map)},
     * both the network system used and the zones are needed, while for {@link #getInitiatorsInNetworkZones(NetworkLite, List)} only the
     * zones are needed. This solution was to support both calling functions.
     * <p>
     * Note if a zone is found that has more than one of the initiator, the zone will be returned once for each initiator.
     * 
     * @param network the network of the initiators
     * @param initiators the initiators
     * @param wwnToZones a IN/OUT parameters which is a map to be populated
     *            with the zone mappings found
     * @return the network system used to get the zones.
     */
    private NetworkSystem fetchInitiatorsZones(NetworkLite network,
            List<Initiator> initiators,
            Map<String, List<Zone>> wwnToZones) {

        // Check some network systems are discovered.
        if (!NetworkUtil.areNetworkSystemDiscovered(_dbClient)) {
            return null;
        }
        if (!Transport.FC.toString().equals(network.getTransportType())) {
            return null;
        }
        if (initiators == null || initiators.isEmpty()) {
            return null;
        }

        // Select the network system to use
        NetworkSystem networkSystem = null;
        Map<String, Initiator> wwnToInitiatorMap = wwnToInitiatorMap(initiators);
        List<NetworkSystem> zoningNetworkSystems = _networkScheduler.getZoningNetworkSystems(network, null);
        Iterator<NetworkSystem> itr = zoningNetworkSystems.iterator();
        while (itr.hasNext()) {
            networkSystem = itr.next();
            try {
                if (networkSystem != null) {
                    _log.info("Trying network system {} for network {} to get initiator zones.",
                            networkSystem.getLabel(), network.getLabel());
                    wwnToZones.putAll(getDevice(networkSystem.getSystemType()).getEndpointsZones(networkSystem,
                            NetworkUtil.getNetworkWwn(network), network.getNativeId(), wwnToInitiatorMap.keySet()));
                    break; // if we get here, we were successful at getting the zones, do not try any more network systems
                }
            } catch (Exception ex) {
                // if we hit and exception, log it and try the next network system;
                wwnToZones.clear();
                _log.error("Failed to get the zones for initiators {} in network {} " +
                        "using network system {}. Will try the other available network systems",
                        new Object[] { wwnToInitiatorMap.keySet(), network.getLabel(),
                                networkSystem == null ? "null" : networkSystem.getLabel() });
            }
            networkSystem = null;
        }
        if (networkSystem == null) {
            _log.error("Failed to find a registered network system in good discovery status to discover the zones");
            throw NetworkDeviceControllerException.exceptions
                    .failedToFindNetworkSystem(wwnToInitiatorMap.keySet(), network.getLabel());
        }
        return networkSystem;
    }

    /**
     * For the given network and initiators, which are in the network,
     * and a given list of storage ports, find all the zones on the network
     * system for the initiators. Search the zones to find ones that have
     * one or more of the ports and create the zoning map between the
     * initiators and ports. Returns the results as {@link ZoneInfoMap} which is map
     * of initiator port WWN and storage port WWN keyed by zone-key, where zone-key
     * is the concatenation of the initiator port WWN and the storage port WWN.
     * <p>
     * Note that the map returned contains only the zones that were selected for use by ViPR. In the case of duplicate zones between an
     * initiator-port pair, ViPR applies a selection criteria to choose one. See {@link #selectZonesForInitiatorsAndPorts}
     * <p>
     * Note that a zone in the network system can have more than one initiator and one storage port. For such zone, there can be multiple
     * entries in the map, one for each initiator/port pairs.
     * <p>
     * If the initiator is not in a network or no zones could be found for the initiator, there will be no entries for this initiator in the
     * map. An empty map will be returned if no zones could be found for any initiator.
     * 
     * @param network the network of the initiators
     * @param initiators the initiators for which the zones will be read
     * @param initiatorPortsMap the storage ports of interest in the networks.
     * @return a ZoneInfoMap a map of zones found that have at least one of initiators and one of the ports
     */
    private ZoneInfoMap getInitiatorsInNetworkZoneInfoMap(NetworkLite network, List<Initiator> initiators,
            Map<String, StoragePort> initiatorPortsMap) {
        ZoneInfoMap map = new ZoneInfoMap();
        fetchInitiatorsInNetworkZoneInfoMap(network, map, initiators, initiatorPortsMap);
        return map;
    }

    /**
     * For the given network and initiators, which are in the network,
     * and a given list of storage ports, find all the zones on the network
     * system for the initiators. Search the zones to find ones that have
     * one or more of the ports and create the zoning map between the
     * initiators and ports. Returns the results as {@link ZoneInfoMap} which is map
     * of initiator port WWN and storage port WWN keyed by zone-key, where zone-key
     * is the concatenation of the initiator port WWN and the storage port WWN.
     * <p>
     * Note that the map returned contains only the zones that were selected for use by ViPR. In the case of duplicate zones between an
     * initiator-port pair, ViPR applies a selection criteria to choose one. See {@link #selectZonesForInitiatorsAndPorts}
     * <p>
     * Note that a zone in the network system can have more than one initiator and one storage port. For such zone, there can be multiple
     * entries in the map, one for each initiator/port pairs.
     * <p>
     * If the initiator is not in a network or no zones could be found for the initiator, there will be no entries for this initiator in the
     * map. An empty map will be returned if no zones could be found for any initiator.
     * 
     * @param network the network of the initiators
     * @param map an OUT parameter where ZoneInfoMap is stored
     * @param initiators the initiators for which the zones will be read
     * @param initiatorPortsMap the storage ports of interest in the networks.
     * @return the network system used to read the zones
     */
    private NetworkSystem fetchInitiatorsInNetworkZoneInfoMap(NetworkLite network, ZoneInfoMap map,
            List<Initiator> initiators, Map<String, StoragePort> initiatorPortsMap) {
        Map<String, Initiator> wwnToInitiatorMap = wwnToInitiatorMap(initiators);

        // retrieve the zones
        Map<String, List<Zone>> wwnToZones = new HashMap<String, List<Zone>>();
        NetworkSystem networkSystem = fetchInitiatorsZones(network, initiators, wwnToZones);
        wwnToZones = selectZonesForInitiatorsAndPorts(network, wwnToZones, initiatorPortsMap);

        // if we successfully retrieved the zones
        if (networkSystem != null && !wwnToZones.isEmpty()) {
            ZoneInfo info = null;
            Initiator initiator = null;
            for (Map.Entry<String, List<Zone>> entry : wwnToZones.entrySet()) {
                initiator = wwnToInitiatorMap.get(entry.getKey());
                for (Zone zone : entry.getValue()) { // I need some logic here to make sure I select the best zone
                    for (ZoneMember member : zone.getMembers()) {
                        if (initiatorPortsMap.containsKey(member.getAddress())) { // double check WWN formatting
                            StoragePort port = initiatorPortsMap.get(member.getAddress());
                            info = new ZoneInfo();
                            info.setZoneName(zone.getName());
                            info.setInitiatorWwn(initiator.getInitiatorPort());
                            info.setInitiatorId(initiator.getId().toString());
                            info.setPortWwn(port.getPortNetworkId());
                            info.setPortId(port.getId().toString());
                            info.setNetworkId(network.getId().toString());
                            info.setNetworkWwn(NetworkUtil.getNetworkWwn(network));
                            info.setFabricId(network.getNativeId());
                            info.setNetworkSystemId(networkSystem.getId().toString());
                            map.put(info.getZoneReferenceKey(), info);
                        }
                    }
                }
            }
        }
        return networkSystem;
    }

    /**
     * Given the map of all existing zones for a set on initiators and ports,
     * this function selects the zones that should be used by ViPR.
     * 
     * @see NetworkScheduler#selectExistingZoneForInitiatorPort(String, String, List)
     * 
     * @param wwnToZones a map of existing zones
     * @param initiatorPortsMap a map of port-wwn-to-storage-port
     * @return a new map containing the selected zones.
     */
    private Map<String, List<Zone>> selectZonesForInitiatorsAndPorts(NetworkLite network,
            Map<String, List<Zone>> wwnToZones, Map<String, StoragePort> initiatorPortsMap) {
        Map<String, List<Zone>> filteredMap = new HashMap<String, List<Zone>>();
        Zone zone = null;
        List<Zone> zones = null;
        for (String initiatorWwn : wwnToZones.keySet()) {
            for (String portWwn : initiatorPortsMap.keySet()) {
                zone = _networkScheduler.selectExistingZoneForInitiatorPort(network, initiatorWwn, portWwn, wwnToZones.get(initiatorWwn));
                if (zone != null) {
                    zones = filteredMap.get(initiatorWwn);
                    if (zones == null) {
                        zones = new ArrayList<>();
                        filteredMap.put(initiatorWwn, zones);
                    }
                    zones.add(zone);
                }
            }
        }
        return filteredMap;
    }

    /**
     * Given a list of initiators, return a map of initiator-wwn-to-initiators
     * 
     * @param initiators the list of initiators
     * @return a map of initiator-wwn-to-initiators
     */
    private Map<String, Initiator> wwnToInitiatorMap(List<Initiator> initiators) {
        Map<String, Initiator> wwns = new HashMap<String, Initiator>();
        for (Initiator initiator : initiators) {
            if (HostInterface.Protocol.FC.toString().equals(initiator.getProtocol())) {
                wwns.put(initiator.getInitiatorPort(), initiator);
            }
        }
        return wwns;
    }

    /**
     * Given a list of initiators, and a list of ports, for each initiator, find the
     * zones on the network system where the initiator port WWN and the storage port
     * WWN is a member. Returns the results as {@link ZoneInfoMap} which is map
     * of initiator port WWN and storage port WWN keyed by zone-key, where zone-key
     * is the concatenation of the initiator port WWN and the storage port WWN.
     * <p>
     * Note that a zone in the network system can have more than one initiator and one storage port. For such zone, there can be multiple
     * entries in the map, one for each initiator/port pairs.
     * <p>
     * If the initiator is not in a network or no zones could be found for the initiator, there will be no entries for this initiator in the
     * map. An empty map will be returned if no zones could be found for any initiator.
     * 
     * @param initiators the list of initiators.
     * @param storagePorts
     * @return an instance of {@link ZoneInfoMap} which is which is map
     *         of initiator port WWN and storage port WWN keyed by zone-key, where zone-key
     *         is the concatenation of the initiator port WWN and the storage port WWN.
     */
    public ZoneInfoMap getInitiatorsZoneInfoMap(List<Initiator> initiators, List<StoragePort> storagePorts) {
        ZoneInfoMap zoningMap = new ZoneInfoMap();
        Map<NetworkLite, List<Initiator>> initiatorsByNetworkMap = NetworkUtil.getInitiatorsByNetwork(initiators, _dbClient);
        for (Map.Entry<NetworkLite, List<Initiator>> entry : initiatorsByNetworkMap.entrySet()) {
            if (!Transport.FC.toString().equals(entry.getKey().getTransportType())) {
                continue;
            }
            Map<String, StoragePort> initiatorPortsMap = NetworkUtil.getPortsInNetworkMap(entry.getKey(), storagePorts);
            if (initiatorPortsMap.size() > 0) {
                zoningMap.putAll(getInitiatorsInNetworkZoneInfoMap(entry.getKey(), entry.getValue(), initiatorPortsMap));
            }
        }
        return zoningMap;
    }

    /**
     * Given a list of initiators, for each initiator, find the zones on the network
     * system where the initiator port WWN is a member. Returns the results as a map
     * of zones grouped by the initiator port WWN.
     * 
     * @param initiators the list of initiators.
     * @return map of zones grouped the initiator port WWN
     */
    public Map<String, List<Zone>> getInitiatorsZones(Collection<Initiator> initiators) {
        Map<String, List<Zone>> zonesMap = new HashMap<String, List<Zone>>();
        Map<NetworkLite, List<Initiator>> initiatorsByNetworkMap = NetworkUtil.getInitiatorsByNetwork(initiators, _dbClient);
        for (Map.Entry<NetworkLite, List<Initiator>> entry : initiatorsByNetworkMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                zonesMap.putAll(getInitiatorsInNetworkZones(entry.getKey(), entry.getValue()));
            }
        }
        return zonesMap;
    }

    /**
     * Finds all the zone paths that exists between a list of initiators and storage ports.
     * 
     * @param initiators the list of initiators
     * @param storagePorts a map of storage port keyed by the port WWN
     * @param initiatorWwnToZonesMap an OUT parameter used to store the zones retrieved mapped by initiator
     * 
     * @return a zoning map of zones that exists on the network systems
     */
    public StringSetMap getZoningMap(NetworkLite network, List<Initiator> initiators,
            Map<String, StoragePort> portsMap, Map<String, List<Zone>> initiatorWwnToZonesMap) {
        StringSetMap map = new StringSetMap();

        // find all the zones for the initiators as a map of initiator WWN to zones
        if (initiatorWwnToZonesMap == null) {
            initiatorWwnToZonesMap = new HashMap<String, List<Zone>>();
        }
        // of the zones retrieved from the network system, select the once
        fetchInitiatorsZones(network, initiators, initiatorWwnToZonesMap);
        initiatorWwnToZonesMap = selectZonesForInitiatorsAndPorts(network, initiatorWwnToZonesMap, portsMap);
        // build the map object
        for (Initiator initiator : initiators) {
            StringSet set = new StringSet();
            List<Zone> zones = initiatorWwnToZonesMap.get(initiator.getInitiatorPort());
            if (zones != null) {
                for (Zone zone : zones) {
                    for (ZoneMember member : zone.getMembers()) {
                        if (portsMap.containsKey(member.getAddress())) {
                            // There can be multiple zones with the same initiator and port
                            // for this function, we're just finding all the mappings
                            set.add(portsMap.get(member.getAddress()).getId().toString());
                        }
                    }
                }
            }
            if (set != null && !set.isEmpty()) {
                map.put(initiator.getId().toString(), set);
            }
        }
        _log.info("Found the following zone mappings {} for initiators {} and ports {}",
                new Object[] { map, initiatorWwnToZonesMap.keySet(), portsMap.keySet() });
        return map;
    }

    /**
     * Update the zoning map for a newly "accepted" export mask. This applies to
     * brown field scenarios where a export mask was found on the storage array.
     * This function finds the zones of the export mask existing initiators and
     * existing ports and creates the zoning map between the two sets.
     * 
     * @param exportGroup the masking view export group
     * @param exportMask the export mask being updated.
     * @param doPersist a boolean that indicates if the changes should be persisted in the db
     */
    public void updateZoningMap(ExportGroup exportGroup, ExportMask exportMask, boolean doPersist) {
        if (exportMask.getCreatedBySystem() == false && exportMask.getExistingInitiators() != null
                && !exportMask.getExistingInitiators().isEmpty()) {
            // we have a mask that was not created by ViPR
            if (exportMask.getZoningMap() == null || exportMask.getZoningMap().isEmpty()) {
                // possibly the first time this export mask is processed, populate from existing zones
                List<StoragePort> storagePorts = ExportUtils.getStoragePorts(exportMask, _dbClient);
                List<Initiator> initiators = ExportUtils.getExportMaskExistingInitiators(exportMask, _dbClient);
                Map<NetworkLite, List<Initiator>> initiatorsByNetworkMap = NetworkUtil.getInitiatorsByNetwork(initiators, _dbClient);

                StringSetMap zoningMap = new StringSetMap();
                for (NetworkLite network : initiatorsByNetworkMap.keySet()) {
                    if (!Transport.FC.toString().equals(network.getTransportType())) {
                        continue;
                    }
                    Map<String, StoragePort> initiatorPortsMap = NetworkUtil.getPortsInNetworkMap(network, storagePorts);
                    if (!initiatorPortsMap.isEmpty()) {
                        zoningMap.putAll(getZoningMap(network, initiatorsByNetworkMap.get(network), initiatorPortsMap, null));
                    }
                }
                exportMask.setZoningMap(zoningMap);
                if (doPersist) {
                    _dbClient.updateAndReindexObject(exportMask);
                }
            }
        }
    }

    /**
     * Update the zoning map for as export mask previously "accepted". This applies to
     * brown field scenarios where a export mask was found on the storage array. For
     * those export masks, changes outside of the application are expected and the
     * application should get the latest state before making any changes. This
     * function is called from ExportMaskOperations#refreshZoneMap after all
     * updates to the initiators, ports and volumes were made into the export mask and
     * the export group. The update steps are as follow:
     * <ol>
     * <li>Get the current zones for those initiators that were not added by ViPR and the storage ports that exist in the mask.</li>
     * <li>Diff the current zones with those in the export mask and update the zoning map</li>
     * <li>Update the FCZoneReferences to match the zone updates</li>
     * </ol>
     * Note that ViPR does not keep FCZoneReferences only for volumes created by ViPR. As those
     * volumes are not updated by ExportMaskOperations#refreshZoneMap, no additional code
     * is needed to remove FCZoneReferences for removed volumes.
     * 
     * @param exportMask the export mask being updated.
     * @param removedInitiators the list of initiators that were removed. This is needed because
     *            these were removed from the zoingMap by {@link ExportMask#removeInitiators(Collection)}
     * @param removedPorts the set of storage ports that were removed
     * @param maskUpdated a flag that indicates if an update was made to the mask that requires
     *            a zoning refresh
     * @param persist a boolean that indicates if the changes should be persisted in the db
     */
    public void refreshZoningMap(ExportMask exportMask, Collection<String> removedInitiators,
            Collection<String> removedPorts, boolean maskUpdated, boolean persist) {
        try {
            // check if zoning is enabled for the mask
            if (!zoningEnabled(exportMask)) {
                _log.info("Zoning not enabled for export mask {}. Zoning refresh will not be done",
                        exportMask.getMaskName());
                return;
            }
            if (!(maskUpdated || alwaysRefreshZone())) {
                _log.info("The mask ports and initiators were not modified and alwaysRefreshZones is false" +
                        " Zoning refresh will not be done for mask {}",
                        exportMask.getMaskName());
                return;
            }
            // Do no refresh the zones of backend masking views for performance reasons
            List<Initiator> initiators = ExportUtils.getExportMaskInitiators(exportMask, _dbClient);
            if (ExportMaskUtils.areBackendInitiators(initiators)) {
                _log.info("Mask {} is a backend mask and its zones will not be refreshed",
                        exportMask.getMaskName());
                return;
            }
            _log.info("Refreshing zones for export mask {}. \n\tCurrent initiators " +
                    "in this mask are:  {}. \n\tStorage ports in the mask are : {}. \n\tZoningMap is : {}. " +
                    "\n\tRemoved initiators: {}. \n\tRemoved ports: {}",
                    new Object[] { exportMask.getMaskName(), exportMask.getInitiators(),
                            exportMask.getStoragePorts(), exportMask.getZoningMap(), removedInitiators, removedPorts });
            Long start = System.currentTimeMillis();
            // get the current zones in the network system for initiators and ports
            List<StoragePort> storagePorts = ExportUtils.getStoragePorts(exportMask, _dbClient);
            ZoneInfoMap zoneInfoMap = getInitiatorsZoneInfoMap(initiators, storagePorts);

            // Get the full sets of initiators and ports affected. They will be used to find the FCZoneReferences to refresh
            // These sets include new initiators and ports, existing ones that did not change, as well as removed ones
            List<StoragePort> allStoragePorts = DataObjectUtils.iteratorToList(_dbClient.queryIterativeObjects(StoragePort.class,
                    StringSetUtil.stringSetToUriList(removedPorts)));
            allStoragePorts.addAll(storagePorts);
            List<Initiator> allInitiators = DataObjectUtils.iteratorToList(_dbClient.queryIterativeObjects(Initiator.class,
                    StringSetUtil.stringSetToUriList(removedInitiators)));
            allInitiators.addAll(initiators);

            // Make a copy of the zoning mask - Zones have already been removed for removed initiators, put them back
            // This zoning map will be used to do diff between old and new and to get zone references
            StringSetMap allZonesMap = new StringSetMap();
            StringSetMap tempMap = exportMask.getZoningMap() == null ? new StringSetMap() : exportMask.getZoningMap();
            for (String key : tempMap.keySet()) {
                // when the zoning map is removed prematurely, this ports set is empty but not null
                if (removedInitiators.contains(key) && (tempMap.get(key) == null || tempMap.get(key).isEmpty())) {
                    // this was prematurely cleared, we will assume all ports
                    // were zoned to make sure we clean up all FCZoneReferences
                    allZonesMap.put(key, new StringSet(removedPorts));
                    if (exportMask.getStoragePorts() != null) {
                        allZonesMap.get(key).addAll(exportMask.getStoragePorts());
                    }
                } else {
                    allZonesMap.put(key, new StringSet(tempMap.get(key)));
                }
            }
            // get all the zone references that exist in the database for this export mask.
            Map<String, List<FCZoneReference>> existingRefs = getZoneReferences(allZonesMap, allInitiators, allStoragePorts);

            // initialize results collections
            List<ZoneInfo> addedZoneInfos = new ArrayList<ZoneInfo>();
            List<ZoneInfo> updatedZoneInfos = new ArrayList<ZoneInfo>();
            List<String> removedZonesKeys = new ArrayList<String>();

            // Compare old and new zones. Initialize some loop variables.
            ZoneInfo zoneInfo = null;
            String initId = null;
            String portId = null;
            if (exportMask.getZoningMap() == null) {
                exportMask.setZoningMap(new StringSetMap());
            }
            for (Entry<String, ZoneInfo> entry : zoneInfoMap.entrySet()) {
                zoneInfo = entry.getValue();
                initId = zoneInfo.getInitiatorId();
                portId = zoneInfo.getPortId();
                if (exportMask.getZoningMap().containsKey(initId) &&
                        exportMask.getZoningMap().get(initId).contains(portId)) {
                    _log.debug("Zoning between initiator {} and port {} did not change",
                            zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn());
                    // This is accounted for, let's remove it from our diff map
                    allZonesMap.remove(initId, portId);
                    // add the zone info so that it can be updated for changes like zone name change
                    updatedZoneInfos.add(zoneInfo);
                } else {
                    _log.info("New zone was found between initiator {} and port {} and will be added",
                            zoneInfo.getInitiatorWwn(), zoneInfo.getPortWwn());
                    // if this was ViPR allocation, do not add new zones
                    // sometimes zones have more than one initiator or port
                    if (exportMask.hasExistingInitiator(zoneInfo.getInitiatorWwn())) {
                        // This is a new entry, add it to the zoning map
                        exportMask.getZoningMap().put(initId, portId);
                        // add it to the results so that the appropriate FCZoneReferences are added
                        addedZoneInfos.add(zoneInfo);
                    }
                    // This zone is not expected to be in the diff map, but try anyway
                    allZonesMap.remove(initId, portId);
                }
            }

            // If anything is remaining zones in the diff zoning map, these were removed in the network system
            Initiator initiator = null;
            StoragePort port = null;
            for (String key : allZonesMap.keySet()) {
                initiator = DataObjectUtils.findInCollection(allInitiators, key);
                if (allZonesMap.get(key) != null && !allZonesMap.get(key).isEmpty()) {
                    for (String val : allZonesMap.get(key)) {
                        port = DataObjectUtils.findInCollection(allStoragePorts, val);
                        _log.info("Zone between initiator {} and port {} was removed from the network system" +
                                " or no longer belongs to this mask.", key, val);
                        if (port == null || initiator == null) {
                            // the port or initiator were removed at some point
                            exportMask.getZoningMap().remove(key, val);
                            _log.info("Removed zoningMap entry between initiator {} and port {} because " +
                                    "the port and/or the initiator were removed from the mask", key, val);
                        } else if (removedInitiators.contains(key) || removedPorts.contains(val)) {
                            // the port or initiator were removed, remove the zone map entry
                            exportMask.getZoningMap().remove(key, val);
                            _log.info("Removed zoningMap entry between initiator {} and port {} because " +
                                    "the port and/or the initiator were removed from the mask",
                                    initiator.getInitiatorPort(), port.getPortNetworkId());
                        } else if (exportMask.hasExistingInitiator(
                                WWNUtility.getUpperWWNWithNoColons(initiator.getInitiatorPort()))) {
                            exportMask.getZoningMap().remove(key, val);
                            _log.info("Removed zoningMap entry between initiator {} and port {} because " +
                                    "this was a brownfield zone for a brownfield initiator",
                                    initiator.getInitiatorPort(), port.getPortNetworkId());
                        } else {
                            _log.info("The zone between initiator {} and port {} was removed from " +
                                    " the network system but the zoningMap entry will be kept because it was" +
                                    " a ViPR initiator-port assignment", initiator.getInitiatorPort(), port.getPortNetworkId());
                        }
                        if (port != null && initiator != null) {
                            removedZonesKeys.add(FCZoneReference.makeEndpointsKey(initiator.getInitiatorPort(), port.getPortNetworkId()));
                        }
                    }
                }
            }

            // get all the existing zone references from the database, these are
            refreshFCZoneReferences(exportMask,
                    existingRefs, addedZoneInfos, updatedZoneInfos, removedZonesKeys);
            if (persist) {
                _dbClient.updateAndReindexObject(exportMask);
            }
            _log.info("Changed zones for export mask {} to {}. \nRefreshin zones took {} ms",
                    new Object[] { exportMask.getMaskName(), exportMask.getZoningMap(),
                            (System.currentTimeMillis() - start) });
        } catch (Exception ex) {
            _log.error("An exception occurred while updating zoning map for export mask {} with message {}",
                    new Object[] { exportMask.getMaskName(), ex.getMessage() }, ex);
        }
    }

    /**
     * Checks if zoning is enabled for any of the mask export groups.
     * 
     * @param exportMask the export mask
     * @return true if zoning is enabled for any of the mask export groups.
     */
    private boolean zoningEnabled(ExportMask exportMask) {
        if (NetworkUtil.areNetworkSystemDiscovered(_dbClient)) {
            List<ExportGroup> exportGroups = ExportUtils.getExportGroupsForMask(exportMask.getId(), _dbClient);
            for (ExportGroup exportGroup : exportGroups) {
                if (NetworkScheduler.isZoningRequired(_dbClient, exportGroup.getVirtualArray())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Given the list of zone changes, the list of existing zone references for the initiators and ports,
     * update the FCZoneReference instances in the database. This function ensures that the zone
     * references updated are those of the export mask volumes.
     * 
     * @param exportMask the export mask being refreshed
     * @param existingRefs a map of zone-reference-key-to-zone-references for each initiator-port pair
     *            of the mask, including those that were removed. This list may contain zone references for
     *            volumes not in the export mask.
     * @param addedZoneInfos the ZoneInfo instances of zones that were added in this refresh operation
     * @param updatedZoneInfos the ZoneInfo instances of zones that were updated in this refresh operation
     * @param removedZonesKeys the keys of the zones that were removed.
     */
    private void refreshFCZoneReferences(ExportMask exportMask, Map<String, List<FCZoneReference>> existingRefs,
            List<ZoneInfo> addedZoneInfos, List<ZoneInfo> updatedZoneInfos, List<String> removedZonesKeys) {

        // get the export mask volumes and export groups because FCZoneReference are kept for each
        Map<URI, Integer> exportMaskVolumes = StringMapUtil.stringMapToVolumeMap(exportMask.getVolumes());
        List<ExportGroup> exportGroups = ExportUtils.getExportGroupsForMask(exportMask.getId(), _dbClient);

        // start with removing references
        List<FCZoneReference> temp = null;
        List<FCZoneReference> refs = new ArrayList<FCZoneReference>();
        for (String refKey : removedZonesKeys) {
            temp = existingRefs.get(refKey);
            if (temp == null) {
                continue;
            }
            for (FCZoneReference ref : temp) {
                for (ExportGroup exportGroup : exportGroups) {
                    if (exportGroup.getId().equals(ref.getGroupUri()) &&
                            exportGroup.hasBlockObject(ref.getVolumeUri()) &&
                            exportMaskVolumes.containsKey(ref.getVolumeUri()) /*
                                                                               * &&
                                                                               * ref.getExistingZone()
                                                                               */) {
                        _log.info("FCZoneReference {} for volume {} and exportGroup {} will be deleted",
                                new Object[] { ref.getPwwnKey(), ref.getVolumeUri(), ref.getGroupUri() });
                        refs.add(ref);
                    }
                }
            }
        }
        _dbClient.markForDeletion(refs);
        refs.clear();

        // update zone references with new zone info in case the zone was renamed
        for (ZoneInfo zoneInfo : updatedZoneInfos) {
            String refKey = zoneInfo.getZoneReferenceKey();
            temp = existingRefs.get(refKey);
            if (temp == null) {
                continue;
            }
            for (FCZoneReference ref : temp) {
                for (ExportGroup exportGroup : exportGroups) {
                    if (exportGroup.getId().equals(ref.getGroupUri()) &&
                            exportGroup.hasBlockObject(ref.getVolumeUri()) &&
                            exportMaskVolumes.containsKey(ref.getVolumeUri())) {
                        // only update when there are changes to avoid unnecessary create/delete of indexes
                        if (zoneInfo.getZoneName() != null && !zoneInfo.getZoneName().equals(ref.getZoneName())) {
                            ref.setZoneName(zoneInfo.getZoneName());
                            ref.setExistingZone(true);
                        }
                        if (zoneInfo.getNetworkSystemId() != null &&
                                (ref.getNetworkSystemUri() == null ||
                                !zoneInfo.getNetworkSystemId().equals(ref.getNetworkSystemUri().toString()))) {
                            ref.setNetworkSystemUri(URI.create(zoneInfo.getNetworkSystemId()));
                        }
                        if (zoneInfo.getFabricId() != null && !zoneInfo.getFabricId().equals(ref.getFabricId())) {
                            ref.setFabricId(zoneInfo.getFabricId());
                        }
                        refs.add(ref);
                    }
                }
            }
        }
        _dbClient.updateAndReindexObject(refs);
        refs.clear();

        // Create zone references as needed, one per volume and export group
        for (ZoneInfo zoneInfo : addedZoneInfos) {
            for (URI volUri : exportMaskVolumes.keySet()) {
                for (ExportGroup exportGroup : exportGroups) {
                    if (exportGroup.hasBlockObject(volUri)) {
                        refs.add(createFCZoneReference(zoneInfo, volUri, exportGroup)); // do I need to check duplicates?
                        _log.info("FCZoneReference {} for volume {} and exportGroup {} will be added",
                                new Object[] { zoneInfo.getZoneReferenceKey(), volUri, exportGroup.getId() });
                    }
                }
            }
        }
        _dbClient.createObject(refs);
    }

    /**
     * Given the zoning map, find all the instances of FCZoneReference for each initiator-port pair.
     * 
     * @param refreshMap the zoning map
     * @param initiators the initiators
     * @param ports the storage ports
     * @return a map of zone key to a list of zone reference objects for the key.
     */
    private Map<String, List<FCZoneReference>> getZoneReferences(StringSetMap refreshMap,
            Collection<Initiator> initiators, Collection<StoragePort> ports) {
        Map<String, List<FCZoneReference>> map = new HashMap<String, List<FCZoneReference>>();
        Initiator initiator = null;
        StoragePort port = null;
        Set<String> portsKey = null;
        for (String initKey : refreshMap.keySet()) {
            portsKey = refreshMap.get(initKey);
            initiator = DataObjectUtils.findInCollection(initiators, initKey);
            if (initiator == null) {
                continue;
            }
            if (portsKey == null || portsKey.isEmpty()) {
                continue;
            }
            for (String portkey : portsKey) {
                port = DataObjectUtils.findInCollection(ports, portkey);
                if (port == null) {
                    continue;
                }
                String key = FCZoneReference.makeEndpointsKey(initiator.getInitiatorPort(), port.getPortNetworkId());
                Joiner joiner = dbModelClient.join(FCZoneReference.class, "refs", "pwwnKey", key).go();
                List<FCZoneReference> list = joiner.list("refs");
                if (list != null && !list.isEmpty()) {
                    map.put(key, list);
                }
            }
        }
        return map;
    }

    /**
     * Creates an instance of FCZoneReference
     * 
     * @param info the zone info containing the zone, its network,
     *            its network system, ...
     * @param initiator the zone initiator
     * @param volume volume the FCZoneReference volume
     * @param exportGroup the FCZoneReference export group
     * @return an instance of FCZoneReference
     */
    private static FCZoneReference createFCZoneReference(ZoneInfo info,
            URI volumeURI, ExportGroup exportGroup) {
        FCZoneReference ref = new FCZoneReference();
        ref.setPwwnKey(info.getZoneReferenceKey());
        ref.setFabricId(info.getFabricId());
        ref.setNetworkSystemUri(URI.create(info.getNetworkSystemId()));
        ref.setVolumeUri(volumeURI);
        ref.setGroupUri(exportGroup.getId());
        ref.setZoneName(info.getZoneName());
        ref.setId(URIUtil.createId(FCZoneReference.class));
        ref.setLabel(FCZoneReference.makeLabel(ref.getPwwnKey(), volumeURI.toString()));
        ref.setExistingZone(true);
        return ref;
    }

    /**
     * Returns the flag settable by the user in the system config that indicates if zoned should
     * be refreshed each time a mask is changed in viPR.
     * 
     * @return true/false
     */
    private boolean alwaysRefreshZone() {
        boolean alwaysRefresh = false; // default to true
        try {
            alwaysRefresh = Boolean.valueOf(ControllerUtils.getPropertyValueFromCoordinator(
                    _coordinator, "controller_ns_zone_refresh_always"));
        } catch (Exception ex) {
            _log.warn("Failed to get the values for controller_ns_zone_refresh_always from resource bundle "
                    + ex.getMessage());
        }
        return alwaysRefresh;
    }
}
