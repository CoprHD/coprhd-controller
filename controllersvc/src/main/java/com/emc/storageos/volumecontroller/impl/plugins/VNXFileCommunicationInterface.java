/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.nas.vnxfile.xmlapi.TreeQuota;
import com.emc.storageos.cimadapter.connections.cim.CimConnection;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsServerMap;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.StringSetMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObject;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileQuotaDirectory;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileCollectionException;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.vnx.xmlapi.VNXCifsServer;
import com.emc.storageos.vnx.xmlapi.VNXControlStation;
import com.emc.storageos.vnx.xmlapi.VNXDataMover;
import com.emc.storageos.vnx.xmlapi.VNXDataMoverIntf;
import com.emc.storageos.vnx.xmlapi.VNXException;
import com.emc.storageos.vnx.xmlapi.VNXFileSshApi;
import com.emc.storageos.vnx.xmlapi.VNXFileSystem;
import com.emc.storageos.vnx.xmlapi.VNXStoragePool;
import com.emc.storageos.vnx.xmlapi.VNXVdm;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.FileShareExport;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileDiscExecutor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.vnxfile.VNXFileExecutor;
import com.emc.storageos.volumecontroller.impl.smis.CIMConnectionFactory;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

// todo -  vnx exception need be updated COP-32172
// todo - it is better some function to vnxcomm helper class and util-class
/**
 * VNXFileCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to collect statistics from VNX
 * File using XHMP/XMLAPI interface.
 * 
 */
public class VNXFileCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    /**
     * Logger instance to log messages.
     */
    private static final Logger _logger = LoggerFactory.getLogger(VNXFileCommunicationInterface.class);
    private static final String METERINGFILE = "metering-file";
    private static final String DM_ROLE_STANDBY = "standby";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final String VIRTUAL = "VIRTUAL";
    private static final String PHYSICAL = "PHYSICAL";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedExportRule";
    private static final String UNMANAGED_FILEQUOTADIR = "UnManagedFileQuotaDirectory";
    private static final Long TBsINKB = 1073741824L;

    private static int BYTESCONV = 1024;  // VNX defaults to M and apparently Bourne wants K.

    /**
     * Executor to execute the operations.
     */
    private VNXFileExecutor executor;
    private NamespaceList namespaces;

    private VNXFileDiscExecutor _discExecutor;
    private NamespaceList _discNamespaces;

    // getter and setter methods

    /**
     * return the VNXFileExecutor.
     * 
     * @return the _executor
     */
    public VNXFileExecutor getExecutor() {
        return executor;
    }

    /**
     * set the VNXFileExecutor.
     * 
     * @param executor
     *            the _executor to set
     */
    public void setExecutor(final VNXFileExecutor executor) {
        this.executor = executor;
    }

    /**
     * set the VNXFileDiscExecutor.
     * 
     * @param discExec
     */
    public void setDiscExecutor(VNXFileDiscExecutor discExec) {
        _discExecutor = discExec;
    }

    /**
     * return the VNXFileDiscExecutor.
     * 
     * @return
     */
    public VNXFileDiscExecutor getDiscExecutor() {
        return _discExecutor;
    }

    /**
     * set the NamespaceList.
     * 
     * @param namespaces
     */
    public void setDiscNamespaces(NamespaceList namespaces) {
        _discNamespaces = namespaces;
    }

    /**
     * return the _discNamespaces
     * 
     * @return
     */
    public NamespaceList getDiscNamespaces() {
        return _discNamespaces;
    }

    /**
     * Construct the map of input attributes which will be used during the
     * execution and processing the result.
     * 
     * @param accessProfile
     */
    private void populateMap(
            final AccessProfile accessProfile) {
        _logger.debug("Populating input attributes in the map.");
        _keyMap.put(VNXFileConstants.DEVICETYPE, accessProfile.getSystemType());
        _keyMap.put(VNXFileConstants.DBCLIENT, _dbClient);
        _keyMap.put(VNXFileConstants.USERNAME, accessProfile.getUserName());
        _keyMap.put(VNXFileConstants.USER_PASS_WORD, accessProfile.getPassword());
        _keyMap.put(VNXFileConstants.URI, getServerUri(accessProfile));
        _keyMap.put(VNXFileConstants.PORTNUMBER, accessProfile.getPortNumber());
        _keyMap.put(Constants._Stats, new LinkedList<Stat>());
        _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
        _keyMap.put(Constants._serialID, accessProfile.getserialID());
        _keyMap.put(Constants._nativeGUIDs, Sets.newHashSet());
        _keyMap.put(VNXFileConstants.AUTHURI, getLoginUri(accessProfile));
        String globalCacheKey = accessProfile.getserialID() + Constants._minusDelimiter
                + Constants._File;
        _keyMap.put(Constants._globalCacheKey, globalCacheKey);
        _keyMap.put(Constants.PROPS, accessProfile.getProps());
        if (executor != null) {
            executor.setKeyMap(_keyMap);
            _logger.debug("Map set on executor....");
        }
    }

    /**
     * return the XML API Server uri.
     * 
     * @param accessProfile
     *            : accessProfile to get the credentials.
     * @return uri.
     */
    private String getServerUri(final AccessProfile accessProfile) {
        try {
            final URI deviceURI = new URI("https", accessProfile.getIpAddress(), "/servlets/CelerraManagementServices", null);
            return deviceURI.toString();
        } catch (URISyntaxException ex) {
            _logger.error("Error while creating server uri for IP {}", accessProfile.getIpAddress());
        }

        return "";

    }

    /**
     * return the XML API Server Login uri.
     * 
     * @param accessProfile
     *            : accessProfile to get the credentials.
     * @return uri.
     */
    private String getLoginUri(final AccessProfile accessProfile) {
        try {
            final URI deviceURI = new URI("https", accessProfile.getIpAddress(), "/Login", null);
            return deviceURI.toString();
        } catch (URISyntaxException ex) {
            _logger.error("Error while creating login uri for IP {}", accessProfile.getIpAddress());
        }

        return "";
    }

    /**
     * Check and add valid SMIS connection for storage system to ConnectionManager.
     * 
     * @param accessProfile
     *            : AccessProfile for the providers
     * @param StorageSystem
     *            : Storage system
     * @return boolean : true if connection can be establish
     */
    private boolean getVnxFileSMISConnection(AccessProfile accessProfile, StorageSystem system) {
        try {
            final CIMConnectionFactory connectionFactory = (CIMConnectionFactory) accessProfile
                    .getCimConnectionFactory();
            // getConnection method also add the valid connection to connection manager.
            CimConnection cxn = connectionFactory.getConnection(system);
            if (cxn != null && connectionFactory.checkConnectionliveness(cxn)) {
                return true;
            }
        } catch (final Exception ex) {
            _logger.error("Not able to get CIMOM Client instance for provider ip {} due to ",
                    system.getSmisProviderIP(), ex);
        }
        return false;
    }

    /**
     * Stop the Plug-in Thread by gracefully clearing allocated resources.
     */
    @Override
    public void cleanup() {
        _logger.info("Stopping the Plugin Thread and clearing Resources");
        releaseResources();
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        try {
            _logger.info("Start collecting statistics for ip address {}",
                    accessProfile.getIpAddress());
            // construct the map and use the request attributes
            // to execute operations & process the result.
            populateMap(accessProfile);
            // Read the operations and execute them.
            executor.execute((Namespace) namespaces.getNsList().get(METERINGFILE));
            dumpStatRecords();
            injectStats();
            _logger.info("End collecting statistics for ip address {}",
                    accessProfile.getIpAddress());
        } finally {
            releaseResources();
        }
    }

    /**
     * releaseResources
     */
    private void releaseResources() {
        executor = null;
        namespaces = null;
    }

    public void setNamespaces(NamespaceList namespaces) {
        this.namespaces = namespaces;
    }

    public NamespaceList getNamespaces() {
        return namespaces;
    }

    /**
     * Discover a VNX File Storage System. Query the Control Station, Storage Pools, Data Movers, and the
     * Network Interfaces for each Data Mover.
     * 
     * @param accessProfile access profile contains credentials to contact the device.
     * @throws BaseCollectionException
     */
    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        _logger.info("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                accessProfile.getIpAddress() + ":" + accessProfile.getPortNumber(),
                accessProfile.getnamespace());

        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                                .toString()))) {
            discoverUmanagedFileSystems(accessProfile);
            // discoverUnmanagedExports(accessProfile);
            discoverUnmanagedNewExports(accessProfile);
            discoverUnManagedCifsShares(accessProfile);
        } else {
            discoverAll(accessProfile);
        }
    }

    // todo - split and optimize COP-32172
    /**
     * Discovery of vnxfile storagesystem.( port, portgroup pools, vdm)
     * 
     * @param accessProfile - AccessProfile for the providers
     * @throws BaseCollectionException
     */
    public void discoverAll(AccessProfile accessProfile) throws BaseCollectionException {
        URI storageSystemId = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            _logger.info("Access Profile Details :  IpAddress : {}, PortNumber : {}", accessProfile.getIpAddress(),
                    accessProfile.getPortNumber());
            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
            // Retrieve control station information.
            discoverControlStation(storageSystem);

            // Model number
            VNXFileSshApi sshDmApi = new VNXFileSshApi();
            sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(),
                    storageSystem.getPassword());
            String model = sshDmApi.getModelInfo();
            storageSystem.setModel(model);

            boolean connectionStatus = getVnxFileSMISConnection(accessProfile, storageSystem);
            if (connectionStatus) {
                storageSystem.setSmisConnectionStatus(ConnectionStatus.CONNECTED.toString());
            } else {
                storageSystem.setSmisConnectionStatus(ConnectionStatus.NOTCONNECTED.toString());
            }

            _dbClient.updateObject(storageSystem);
            if (!storageSystem.getReachableStatus()) {
                throw new VNXFileCollectionException("Failed to connect to " + storageSystem.getIpAddress());
            }

            // Get All Existing DataMovers
            Map<String, StorageHADomain> allExistingDataMovers = getAllDataMovers(storageSystem);
            for (StorageHADomain activeDM : allExistingDataMovers.values()) {
                _logger.info("Existing DataMovers in database {}", activeDM.getName());
            }

            // Discover port groups (data movers)
            StringSet fileSharingProtocols = new StringSet();
            Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, fileSharingProtocols);
            _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
            _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
            if (!groups.get(NEW).isEmpty()) {
                _dbClient.createObject(groups.get(NEW));
                for (StorageHADomain newDm : groups.get(NEW)) {
                    _logger.info("New DM {} ", newDm.getAdapterName());
                }
            }

            if (!groups.get(EXISTING).isEmpty()) {
                _dbClient.updateObject(groups.get(EXISTING));
                for (StorageHADomain existingDm : groups.get(EXISTING)) {
                    _logger.info("Existing DM {} ", existingDm.getAdapterName());
                }
            }

            // Discover storage pools.
            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            Map<String, List<StoragePool>> pools = discoverStoragePools(storageSystem, poolsToMatchWithVpool, fileSharingProtocols);

            _logger.info("No of newly discovered pools {}", pools.get(NEW).size());
            _logger.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if (!pools.get(NEW).isEmpty()) {
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if (!pools.get(EXISTING).isEmpty()) {
                allPools.addAll(pools.get(EXISTING));
                _dbClient.updateObject(pools.get(EXISTING));
            }
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(
                    allPools, _dbClient, storageSystemId);
            if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                poolsToMatchWithVpool.addAll(notVisiblePools);
            }
            // Keep a set of active data movers. Data movers in 'standby' state are not added to the
            // database since they cannot be used in this state.
            Set<StorageHADomain> activeDataMovers = new HashSet<StorageHADomain>();
            activeDataMovers.addAll(groups.get(NEW));
            activeDataMovers.addAll(groups.get(EXISTING));

            int i = 0;
            for (StorageHADomain activeDM : activeDataMovers) {
                _logger.info("DataMover {} : {}", i++, activeDM.getName());
            }

            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if (!ports.get(NEW).isEmpty()) {
                _dbClient.createObject(ports.get(NEW));
            }

            if (!ports.get(EXISTING).isEmpty()) {
                _dbClient.updateObject(ports.get(EXISTING));
            }

            // Discover VDM and Ports

            Map<String, StorageHADomain> allVdmsInDb = this.getAllVDMs(storageSystem);

            for (StorageHADomain activeVDM : allVdmsInDb.values()) {
                _logger.info("Existing DataMovers in the Database {}", activeVDM.getName());
            }

            Map<String, List<StorageHADomain>> vdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            _logger.info("No of newly Vdm discovered groups {}", vdms.get(NEW).size());
            _logger.info("No of existing vdm discovered groups {}", vdms.get(EXISTING).size());
            if (!vdms.get(NEW).isEmpty()) {
                _dbClient.createObject(vdms.get(NEW));
            }

            if (!vdms.get(EXISTING).isEmpty()) {
                _dbClient.updateObject(vdms.get(EXISTING));
                for (StorageHADomain existingVdm : vdms.get(EXISTING)) {
                    _logger.info("Existing VDM {}", existingVdm.getAdapterName());
                }
            }

            // Keep a set of active data movers. Data movers in 'standby' state are not added to the
            // database since they cannot be used in this state.
            Set<StorageHADomain> activeVDMs = new HashSet<StorageHADomain>();
            List<StorageHADomain> newVdms = vdms.get(NEW);
            for (StorageHADomain vdm : newVdms) {
                _logger.debug("New VDM : {}", vdm.getName());
                activeVDMs.add(vdm);
            }
            List<StorageHADomain> existingVdms = vdms.get(EXISTING);
            for (StorageHADomain vdm : existingVdms) {
                _logger.debug("Existing VDM : {}", vdm.getName());
                activeVDMs.add(vdm);
            }

            // Discover VDM Interfaces
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> vdmPorts = discoverVdmPorts(storageSystem, activeVDMs);

            _logger.info("No of newly discovered port {}", vdmPorts.get(NEW).size());
            _logger.info("No of existing discovered port {}", vdmPorts.get(EXISTING).size());
            if (!vdmPorts.get(NEW).isEmpty()) {
                _dbClient.createObject(vdmPorts.get(NEW));
                for (StoragePort port : vdmPorts.get(NEW)) {
                    _logger.debug("New VDM Port : {}", port.getPortName());
                }
            }

            if (!vdmPorts.get(EXISTING).isEmpty()) {
                _dbClient.updateObject(vdmPorts.get(EXISTING));
                for (StoragePort port : vdmPorts.get(EXISTING)) {
                    _logger.info("EXISTING VDM Port : {}", port.getPortName());
                }
            }

            List<StoragePort> allExistingPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            allExistingPorts.addAll(vdmPorts.get(EXISTING));
            List<StoragePort> allNewPorts = new ArrayList<StoragePort>(ports.get(NEW));
            allNewPorts.addAll(vdmPorts.get(NEW));
            List<StoragePort> allPorts = new ArrayList<StoragePort>(allExistingPorts);
            allPorts.addAll(allNewPorts);
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                    _dbClient, storageSystemId);
            allExistingPorts.addAll(notVisiblePorts);
            StoragePortAssociationHelper.updatePortAssociations(allNewPorts, _dbClient);
            StoragePortAssociationHelper.updatePortAssociations(allExistingPorts, _dbClient);
            StringBuffer errorMessage = new StringBuffer();
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVpool(poolsToMatchWithVpool, _dbClient, _coordinator,
                    storageSystemId, errorMessage);

            // Update the virtual nas association with virtual arrays!!!
            // For existing virtual nas ports!!
            StoragePortAssociationHelper.runUpdateVirtualNasAssociationsProcess(allExistingPorts, null, _dbClient);

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Storage System: %s",
                    storageSystemId.toString());
        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for Storage System: %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new VNXFileCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Create Virtual NAS for the specified VNX File storage array
     * 
     * @param system storage system information including credentials.
     * @param discovered VDM of the specified VNX File storage array
     * @return Virtual NAS Server
     * @throws VNXFileCollectionException
     */
    private VirtualNAS createVirtualNas(StorageSystem system, VNXVdm vdm) throws VNXFileCollectionException {

        VirtualNAS vNas = new VirtualNAS();

        vNas.setNasName(vdm.getVdmName());
        vNas.setStorageDeviceURI(system.getId());
        vNas.setNativeId(vdm.getVdmId());
        vNas.setNasState(vdm.getState());
        vNas.setId(URIUtil.createId(VirtualNAS.class));

        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, vdm.getVdmId(), NativeGUIDGenerator.VIRTUAL_NAS);
        vNas.setNativeGuid(nasNativeGuid);

        PhysicalNAS parentNas = findPhysicalNasByNativeId(system, vdm.getMoverId());

        if (parentNas != null) {
            vNas.setParentNasUri(parentNas.getId());

            StringMap dbMetrics = vNas.getMetrics();
            _logger.info("new Virtual NAS created with guid {} ", vNas.getNativeGuid());

            // Set the Limit Metric keys!!
            Long MaxObjects = 2048L;
            Long MaxCapacity = 200L * TBsINKB;
            String modelStr = system.getModel();
            if (modelStr.startsWith("VNX")) {
                if (Long.parseLong(modelStr.substring(3)) > 5300) {
                    MaxCapacity = 256L * TBsINKB;
                }
            }

            dbMetrics.put(MetricsKeys.maxStorageCapacity.name(), String.valueOf(MaxCapacity));
            dbMetrics.put(MetricsKeys.maxStorageObjects.name(), String.valueOf(MaxObjects));
            vNas.setMetrics(dbMetrics);

            _logger.info("Virtual NAS and its maxStorageCapacity {} and maxStorageObject {}", MaxCapacity,
                    MaxObjects);

        }

        return vNas;

    }

    /**
     * Create Physical NAS for the specified VNX File storage array
     * 
     * @param system storage system information including credentials.
     * @param discovered DM of the specified VNX File storage array
     * @return Physical NAS Server
     * @throws VNXFileCollectionException
     */
    private PhysicalNAS createPhysicalNas(StorageSystem system, VNXDataMover dm) throws VNXFileCollectionException {

        PhysicalNAS phyNas = new PhysicalNAS();
        if (phyNas != null) {
            phyNas.setNasName(dm.getName());
            phyNas.setStorageDeviceURI(system.getId());
            phyNas.setNativeId(String.valueOf(dm.getId()));
            phyNas.setNasState(dm.getRole());
            phyNas.setId(URIUtil.createId(PhysicalNAS.class));
            // Set storage port details to vNas
            String physicalNasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, String.valueOf(dm.getId()), NativeGUIDGenerator.PHYSICAL_NAS);
            phyNas.setNativeGuid(physicalNasNativeGuid);
            _logger.info("Physical NAS created with guid {} ", phyNas.getNativeGuid());

            StringMap dbMetrics = phyNas.getMetrics();
            // Set the Limit Metric keys!!
            Long MaxObjects = 2048L;
            Long MaxCapacity = 200L * TBsINKB;
            String modelStr = system.getModel();
            if (modelStr.startsWith("VNX")) {
                if (Long.parseLong(modelStr.substring(3)) > 5300) {
                    MaxCapacity = 256L * TBsINKB;
                }
            }

            dbMetrics.put(MetricsKeys.maxStorageCapacity.name(), String.valueOf(MaxCapacity));
            dbMetrics.put(MetricsKeys.maxStorageObjects.name(), String.valueOf(MaxObjects));

            _logger.info("Physical NAS and its maxStorageCapacity {} and maxStorageObject {}", String.valueOf(MaxCapacity),
                    String.valueOf(MaxObjects));
            phyNas.setMetrics(dbMetrics);

        }
        return phyNas;

    }

    @Override
    public void scan(AccessProfile arg0) throws BaseCollectionException {
        // TODO Auto-generated method stub
    }

    /**
     * Discover the Control Station for the specified VNX File storage array. Since the StorageSystem object
     * currently exists, this method updates information in the object.
     * 
     * @param system
     * @throws VNXFileCollectionException
     */
    private void discoverControlStation(StorageSystem system) throws VNXFileCollectionException {

        _logger.info("Start Control Station discovery for storage system {}", system.getId());
        VNXControlStation tmpSystem = null;
        try {
            tmpSystem = getControlStation(system);
        } catch (VNXException e) {
            throw new VNXFileCollectionException("Get control station op failed", e);
        }

        if (tmpSystem != null) {
            String sysNativeGuid = NativeGUIDGenerator.generateNativeGuid(DiscoveredDataObject.Type.vnxfile.toString(),
                    tmpSystem.getSerialNumber());
            system.setNativeGuid(sysNativeGuid);
            system.setSerialNumber(tmpSystem.getSerialNumber());
            String firmwareVersion = tmpSystem.getSoftwareVersion();
            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(system.getSystemType()));

            // Example version String for VNX looks like 7.1.56-5.
            firmwareVersion = firmwareVersion.replaceAll("-", ".").trim();
            minimumSupportedVersion = minimumSupportedVersion.replaceAll("-", ".");
            system.setFirmwareVersion(firmwareVersion);

            _logger.info("Verifying version details : Minimum Supported Version {} - Discovered VNX Version {}", minimumSupportedVersion,
                    firmwareVersion);
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, firmwareVersion) < 0) {
                system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                system.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, system.getId());
                VNXFileCollectionException vnxe = new VNXFileCollectionException(String.format(
                        " ** This version of VNX File is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw vnxe;
            }
            system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            system.setReachableStatus(true);
        } else {
            _logger.error("Failed to retrieve control station info!");
            system.setReachableStatus(false);
        }

        _logger.info("Control Station discovery for storage system {} complete", system.getId());
    }

    /**
     * Returns the list of storage pools for the specified VNX File storage system.
     * 
     * @param system storage system information including credentials.
     * @return Map of New and Existing known storage pools.
     * @throws VNXFileCollectionException
     */
    private Map<String, List<StoragePool>> discoverStoragePools(StorageSystem system,
            List<StoragePool> poolsToMatchWithVpool,
            StringSet fileSharingProtocols)
            throws VNXFileCollectionException, VNXException {

        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        _logger.info("Start storage pool discovery for storage system {}", system.getId());
        try {
            List<VNXStoragePool> pools = getStoragePools(system);

            for (VNXStoragePool vnxPool : pools) {
                StoragePool pool = null;

                URIQueryResultList results = new URIQueryResultList();
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vnxPool.getPoolId(), NativeGUIDGenerator.POOL);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                        results);
                Iterator<URI> iter = results.iterator();
                while (iter.hasNext()) {
                    StoragePool tmpPool = _dbClient.queryObject(StoragePool.class, iter.next());

                    if (tmpPool != null && !tmpPool.getInactive() &&
                            tmpPool.getStorageDevice().equals(system.getId())) {
                        pool = tmpPool;
                        _logger.info("Found StoragePool {} at {}", pool.getPoolName(), poolNativeGuid);
                        break;
                    }
                }

                if (pool == null) {
                    pool = new StoragePool();
                    pool.setId(URIUtil.createId(StoragePool.class));

                    pool.setLabel(poolNativeGuid);
                    pool.setNativeGuid(poolNativeGuid);
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.toString());
                    pool.setPoolServiceType(PoolServiceType.file.toString());
                    pool.setStorageDevice(system.getId());
                    pool.setProtocols(fileSharingProtocols);
                    pool.setNativeId(vnxPool.getPoolId());
                    pool.setPoolName(vnxPool.getName());

                    // Supported resource type indicates what type of file systems are supported.
                    if ("true".equalsIgnoreCase(vnxPool.getVirtualProv())) {
                        pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THICK_ONLY.toString());
                    } else {
                        pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString());
                    }
                    pool.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _logger.info("Creating new storage pool using NativeGuid : {}", poolNativeGuid);
                    newPools.add(pool);
                } else {
                    // Set protocols if it has changed between discoveries or a upgrade scenario
                    pool.setProtocols(fileSharingProtocols);
                    existingPools.add(pool);
                }

                long size = 0;
                if (vnxPool.getDynamic().equals("true")) {
                    _logger.info("Using auto size for capacity.");
                    size = Long.parseLong(vnxPool.getAutoSize());
                } else {
                    size = Long.parseLong(vnxPool.getSize());
                }
                pool.setTotalCapacity(size * BYTESCONV);

                long used = Long.parseLong(vnxPool.getUsedSize()) * BYTESCONV;
                long free = pool.getTotalCapacity() - used;
                if (0 > free) {
                    free = 0;
                }
                pool.setFreeCapacity(free);
                pool.setSubscribedCapacity(used);

                if (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                        DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())) {
                    poolsToMatchWithVpool.add(pool);
                }
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
            _logger.info("Number of pools found {} : ", storagePools.size());
        } catch (NumberFormatException e) {
            _logger.error("Data Format Exception:  Discovery of storage pools failed for storage system {} for {}",
                    system.getId(), e.getMessage());

            VNXFileCollectionException vnxe = new VNXFileCollectionException("Storage pool discovery data error for storage system "
                    + system.getId());
            vnxe.initCause(e);

            throw vnxe;
        }
        _logger.info("Storage pool discovery for storage system {} complete", system.getId());
        for (StoragePool newPool : newPools) {
            _logger.info("New Storage Pool : " + newPool);
            _logger.info("New Storage Pool : {} : {}", newPool.getNativeGuid(), newPool.getId());
        }
        for (StoragePool pool : existingPools) {
            _logger.info("Old Storage Pool : " + pool);
            _logger.info("Old Storage Pool : {} : {}", pool.getNativeGuid(), pool.getId());
        }
        // return storagePools;
        storagePools.put(this.NEW, newPools);
        storagePools.put(this.EXISTING, existingPools);
        return storagePools;
    }

    /**
     * Discover the Data Movers (Port Groups) for the specified VNX File storage array.
     * 
     * @param system storage system information including credentials.
     * @return Map of New and Existing port groups
     * @throws VNXFileCollectionException
     */
    private HashMap<String, List<StorageHADomain>> discoverPortGroups(StorageSystem system,
            StringSet fileSharingProtocols)
            throws VNXFileCollectionException, VNXException {
        HashMap<String, List<StorageHADomain>> portGroups = new HashMap<String, List<StorageHADomain>>();

        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();
        boolean isNfsCifsSupported = false;

        List<PhysicalNAS> newNasServers = new ArrayList<PhysicalNAS>();
        List<PhysicalNAS> existingNasServers = new ArrayList<PhysicalNAS>();

        _logger.info("Start port group discovery for storage system {}", system.getId());

        List<VNXDataMover> dataMovers = getPortGroups(system);
        _logger.debug("Number movers found: {}", dataMovers.size());
        for (VNXDataMover mover : dataMovers) {
            StorageHADomain portGroup = null;

            if (null == mover) {
                _logger.debug("Null data mover in list of port groups.");
                continue;
            }
            if (mover.getRole().equals(DM_ROLE_STANDBY)) {
                _logger.debug("Found standby data mover");
                continue;
            }

            // Check if port group was previously discovered
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, mover.getName(), NativeGUIDGenerator.ADAPTER);
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                    results);
            Iterator<URI> iter = results.iterator();
            while (iter.hasNext()) {
                StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, iter.next());

                if (tmpGroup != null && !tmpGroup.getInactive()
                        && tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                    portGroup = tmpGroup;
                    _logger.debug("Found duplicate {} ", mover.getName());
                }
            }

            List<VNXCifsServer> cifsServers = getCifServers(system, String.valueOf(mover.getId()), "false");
            CifsServerMap cifsServersMap = new CifsServerMap();

            if (null != cifsServers && !cifsServers.isEmpty()) {
                for (VNXCifsServer cifsServer : cifsServers) {
                    _logger.info("Cifs Server {} for {} ", cifsServer.getName(), mover.getName());

                    NasCifsServer nasCifsServer = new NasCifsServer();
                    nasCifsServer.setId(cifsServer.getId());
                    nasCifsServer.setInterfaces(cifsServer.getInterfaces());
                    nasCifsServer.setMoverIdIsVdm(cifsServer.getMoverIdIsVdm());
                    nasCifsServer.setName(cifsServer.getName());
                    nasCifsServer.setType(cifsServer.getType());
                    nasCifsServer.setDomain(cifsServer.getDomain());
                    cifsServersMap.put(cifsServer.getName(), nasCifsServer);

                }
            }

            // Check supported network file sharing protocols.
            StringSet protocols = new StringSet();
            protocols.add(StorageProtocol.File.NFS.name());
            protocols.add(StorageProtocol.File.CIFS.name());

            // If the data mover (aka port group) was not previously discovered
            if (portGroup == null) {
                portGroup = new StorageHADomain();
                portGroup.setId(URIUtil.createId(StorageHADomain.class));
                portGroup.setNativeGuid(adapterNativeGuid);
                portGroup.setStorageDeviceURI(system.getId());
                portGroup.setAdapterName(mover.getName());
                portGroup.setVirtual(false);
                portGroup.setName((Integer.toString(mover.getId())));
                portGroup.setFileSharingProtocols(protocols);
                _logger.info("Found data mover {} at {}", mover.getName(), mover.getId());

                newPortGroups.add(portGroup);
            } else {
                // Set protocols if it has changed between discoveries or a upgrade scenario
                portGroup.setFileSharingProtocols(protocols);
                existingPortGroups.add(portGroup);
            }

            PhysicalNAS existingNas = findPhysicalNasByNativeId(system, String.valueOf(mover.getId()));
            if (existingNas != null) {
                existingNas.setProtocols(protocols);
                existingNas.setCifsServersMap(cifsServersMap);
                existingNasServers.add(existingNas);

            } else {
                PhysicalNAS physicalNas = createPhysicalNas(system, mover);
                if (physicalNas != null) {
                    physicalNas.setProtocols(protocols);
                    physicalNas.setCifsServersMap(cifsServersMap);
                    newNasServers.add(physicalNas);
                }
            }

        }

        // Persist the NAS servers!!!
        if (existingNasServers != null && !existingNasServers.isEmpty()) {
            _logger.info("discoverPortGroups - modified PhysicalNAS servers size {}", existingNasServers.size());
            _dbClient.updateObject(existingNasServers);
        }

        if (newNasServers != null && !newNasServers.isEmpty()) {
            _logger.info("discoverPortGroups - new PhysicalNAS servers size {}", newNasServers.size());
            _dbClient.createObject(newNasServers);
        }

        // With current API, NFS/CIFS is assumed to be always supported.
        fileSharingProtocols.add(StorageProtocol.File.NFS.name());
        fileSharingProtocols.add(StorageProtocol.File.CIFS.name());

        _logger.info("Port group discovery for storage system {} complete.", system.getId());
        for (StorageHADomain newDomain : newPortGroups) {
            _logger.info("New Storage Domain : {} : {}", newDomain.getNativeGuid(), newDomain.getAdapterName() + ":" + newDomain.getId());
        }
        for (StorageHADomain domain : existingPortGroups) {
            _logger.info("Old Storage Domain : {} : {}", domain.getNativeGuid(), domain.getAdapterName() + ":" + domain.getId());
        }
        // return portGroups;
        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);

        return portGroups;
    }

    /**
     * Retrieve the Data Mover IP Interfaces (aka Storage Ports) for the specified VNX File Storage Array
     * 
     * @param system storage system information including credentials.
     * @return Map of New and Existing Storage Ports
     * @throws VNXFileCollectionException
     * @throws IOException
     */
    private HashMap<String, List<StoragePort>> discoverPorts(StorageSystem system, Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException, IOException {
        _logger.info("Start storage port discovery for storage system {}", system.getId());
        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();
        List<PhysicalNAS> modifiedServers = new ArrayList<PhysicalNAS>();

        // Retrieve the list of data movers interfaces for the VNX File device.
        List<VNXDataMoverIntf> allDmIntfs = getPorts(system);
        List<VNXVdm> vdms = getVdmPortGroups(system);

        // Filter VDM ports
        Map<String, VNXDataMoverIntf> dmIntMap = new HashMap<String, VNXDataMoverIntf>();

        for (VNXDataMoverIntf intf : allDmIntfs) {
            _logger.info("getPorts Adding {} : {}", intf.getName(), intf.getIpAddress());
            dmIntMap.put(intf.getName(), intf);
        }

        // Changes to fix Jira CTRL - 9151
        VNXFileSshApi sshDmApi = new VNXFileSshApi();
        sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());

        // collect VDM interfaces
        if (vdms != null && !vdms.isEmpty()) {
            Map<String, String> vdmIntfs = null;
            for (VNXVdm vdm : vdms) {
                // Sometimes getVdmPortGroups(system) method does not collect all VDM interfaces,
                // So running Collect NFS/CIFS interfaces from nas_server -info command. This will return
                // Interfaces assigned to VDM and not thru CIFS servers
                vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
                if (vdmIntfs != null && !vdmIntfs.isEmpty()) {
                    for (String vdmIF : vdmIntfs.keySet()) {
                        _logger.info("Remove VDM interface {}", vdmIF);
                        dmIntMap.remove(vdmIF);
                    }
                }
            }
        }

        // Got the filtered out DataMover Interfaces
        List<VNXDataMoverIntf> dmIntfs = new ArrayList(dmIntMap.values());

        _logger.info("Number unfiltered mover interfaces found: {}", allDmIntfs.size());
        _logger.info("Number mover interfaces found: {}", dmIntfs.size());

        // Create the list of storage ports.
        for (VNXDataMoverIntf intf : dmIntfs) {
            StoragePort port = null;

            StorageHADomain matchingHADomain = getMatchingMoverById(movers, intf.getDataMoverId());
            // Check for valid data mover
            if (null == matchingHADomain) {
                continue;
            }

            // Check if storage port was already discovered
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, intf.getIpAddress(), NativeGUIDGenerator.PORT);

            port = findExistingPort(portNativeGuid);
            if (null == port) {
                // Since a port was not found, attempt with previous naming convention (ADAPTER instead of PORT)
                String oldNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, intf.getIpAddress(), NativeGUIDGenerator.ADAPTER);

                port = findExistingPort(oldNativeGuid);
                if (null != port) {
                    // found with old naming convention, therefore update name.
                    port.setLabel(portNativeGuid);
                    port.setNativeGuid(portNativeGuid);
                }
            }

            // If data mover interface was not previously discovered, add new storage port
            if (port == null) {
                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setLabel(portNativeGuid);
                port.setTransportType("IP");
                port.setNativeGuid(portNativeGuid);
                port.setStorageDevice(system.getId());
                port.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                port.setPortName(intf.getName());
                port.setPortNetworkId(intf.getIpAddress());
                port.setPortGroup(intf.getDataMoverId());
                port.setStorageHADomain(matchingHADomain.getId());
                _logger.info(
                        "Creating new storage port using NativeGuid : {} name : {}, IP : {}",
                        new Object[] { portNativeGuid, intf.getName(),
                                intf.getIpAddress() });
                newStoragePorts.add(port);
            } else {
                port.setStorageHADomain(matchingHADomain.getId());
                existingStoragePorts.add(port);
            }
            port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());

            // Set storage port details to vNas
            PhysicalNAS nas = findPhysicalNasByNativeId(system, intf.getDataMoverId());
            if (nas != null) {
                if (nas.getStoragePorts() != null && !nas.getStoragePorts().isEmpty()) {
                    if (nas.getStoragePorts().contains(port.getId())) {
                        nas.getStoragePorts().remove(port.getId());
                    }
                }
                nas.getStoragePorts().add(port.getId().toString());
                modifiedServers.add(nas);
                _logger.info("PhysicalNAS : {} : port : {} got modified", nas.getId(), port.getPortName());
            }
        }

        // Persist the changed nas servers!!!
        if (modifiedServers != null && !modifiedServers.isEmpty()) {
            _logger.info("Modified PhysicalNAS servers size {}", modifiedServers.size());
            _dbClient.updateObject(modifiedServers);
        }

        _logger.info("Storage port discovery for storage system {} complete", system.getId());
        for (StoragePort newPort : newStoragePorts) {
            _logger.info("New Storage Port : {} : {}", newPort.getNativeGuid(), newPort.getPortName() + ":" + newPort.getId());
        }
        for (StoragePort port : existingStoragePorts) {
            _logger.info("Old Storage Port : {} : {}", port.getNativeGuid(), port.getPortName() + ":" + port.getId());
        }
        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        return storagePorts;
    }

    /**
     * Find the Virtual NAS by Native ID for the specified VNX File storage array
     * 
     * @param system storage system information including credentials.
     * @param Native id of the specified Virtual NAS
     * @return Virtual NAS Server
     */
    private VirtualNAS findvNasByNativeId(StorageSystem system, String nativeId) {
        URIQueryResultList results = new URIQueryResultList();
        VirtualNAS vNas = null;

        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId, NativeGUIDGenerator.VIRTUAL_NAS);

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getVirtualNASByNativeGuidConstraint(nasNativeGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            VirtualNAS tmpVnas = _dbClient.queryObject(VirtualNAS.class, iter.next());

            if (tmpVnas != null && !tmpVnas.getInactive()) {
                vNas = tmpVnas;
                _logger.info("found virtual NAS {}", tmpVnas.getNativeGuid() + ":" + tmpVnas.getNasName());
                break;
            }
        }
        return vNas;

    }

    /**
     * Find the Physical NAS by Native ID for the specified VNX File storage array
     * 
     * @param system storage system information including credentials.
     * @param Native id of the specified Physical NAS
     * @return Physical NAS Server
     */
    private PhysicalNAS findPhysicalNasByNativeId(StorageSystem system, String nativeId) {
        URIQueryResultList results = new URIQueryResultList();
        PhysicalNAS physicalNas = null;

        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId, NativeGUIDGenerator.PHYSICAL_NAS);

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getPhysicalNasByNativeGuidConstraint(nasNativeGuid),
                results);

        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            PhysicalNAS tmpNas = _dbClient.queryObject(PhysicalNAS.class, iter.next());

            if (tmpNas != null && !tmpNas.getInactive()) {
                physicalNas = tmpNas;
                _logger.info("found physical NAS {}", physicalNas.getNativeGuid() + ":" + physicalNas.getNasName());
                break;
            }
        }
        return physicalNas;

    }

    // todo COP-32172 -split method
    /**
     * Discover the Data Movers (Port Groups) for the specified VNX File storage array.
     * 
     * @param system storage system information including credentials.
     * @param movers Collection of all DataMovers in the VNX File storage array
     * @return Map of New and Existing VDM port groups
     * @throws VNXFileCollectionException
     */
    private HashMap<String, List<StorageHADomain>> discoverVdmPortGroups(StorageSystem system,
            Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException {
        HashMap<String, List<StorageHADomain>> portGroups = new HashMap();

        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();

        _logger.info("Start vdm port group discovery for storage system {}", system.getId());

        List<VirtualNAS> newNasServers = new ArrayList<VirtualNAS>();
        List<VirtualNAS> existingNasServers = new ArrayList<VirtualNAS>();

        List<VNXVdm> vdms = getVdmPortGroups(system);
        _logger.debug("Number VDM found: {}", vdms.size());
        VNXFileSshApi sshDmApi = new VNXFileSshApi();
        sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(),
                system.getPassword());
        for (VNXVdm vdm : vdms) {
            StorageHADomain portGroup = null;
            // Check supported network file sharing protocols.
            StringSet protocols = new StringSet();

            if (null == vdm) {
                _logger.debug("Null vdm in list of port groups.");
                continue;
            }

            // Check if port group was previously discovered
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, vdm.getVdmName(), NativeGUIDGenerator.ADAPTER);
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                    results);
            Iterator<URI> iter = results.iterator();
            while (iter.hasNext()) {
                StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, iter.next());

                if (tmpGroup != null && !tmpGroup.getInactive()
                        && tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                    portGroup = tmpGroup;
                    _logger.debug("Found duplicate {} ", vdm.getVdmName());
                    break;
                }
            }

            Map<String, String> vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
            Set<String> intfs = null;
            if (vdmIntfs != null) {
                intfs = vdmIntfs.keySet();
            }
            // if NFS Interfaces are not there ignore this..
            if (vdmIntfs == null || intfs.isEmpty()) {
                // There are no interfaces for this VDM via nas_server command
                // so ignore this
                _logger.info("Ignoring VDM {} because no NFS interfaces found via ssh query", vdm.getVdmName());
            } else {
                _logger.info("Process VDM {} because  interfaces found {}", vdm.getVdmName(), vdmIntfs.keySet().size());
            }

            for (String intf : intfs) {
                String vdmCapability = vdmIntfs.get(intf);
                _logger.info("Interface {} capability [{}]", vdm.getVdmName() + ":" + intf, vdmCapability);
                if (vdmCapability.contains("cifs")) {
                    _logger.info("{} has CIFS Enabled since interfaces are found ", vdm.getVdmName(), intf + ":" + vdmCapability);
                    protocols.add(StorageProtocol.File.CIFS.name());
                }
                if (vdmCapability.contains("vdm")) {
                    _logger.info("{} has NFS Enabled since interfaces are found ", vdm.getVdmName(), intf + ":" + vdmCapability);
                    protocols.add(StorageProtocol.File.NFS.name());
                }
            }

            List<VNXCifsServer> cifsServers = getCifServers(system, vdm.getVdmId(), "true");
            CifsServerMap cifsServersMap = new CifsServerMap();

            for (VNXCifsServer cifsServer : cifsServers) {
                _logger.info("Cifs Server {} for {} ", cifsServer.getName(), vdm.getVdmName());
                if (!cifsServer.getInterfaces().isEmpty()) {
                    _logger.info("{} has CIFS Enabled since interfaces are found ", vdm.getVdmName(),
                            cifsServer.getName() + ":" + cifsServer.getInterfaces());
                    protocols.add(StorageProtocol.File.CIFS.name());

                    NasCifsServer nasCifsServer = new NasCifsServer();
                    nasCifsServer.setId(cifsServer.getId());
                    nasCifsServer.setInterfaces(cifsServer.getInterfaces());
                    nasCifsServer.setMoverIdIsVdm(cifsServer.getMoverIdIsVdm());
                    nasCifsServer.setName(cifsServer.getName());
                    nasCifsServer.setType(cifsServer.getType());
                    nasCifsServer.setDomain(cifsServer.getDomain());
                    cifsServersMap.put(cifsServer.getName(), nasCifsServer);
                }
            }

            if (protocols.isEmpty()) {
                // No valid interfaces found and ignore this
                _logger.info("Ignoring VDM {} because no NFS/CIFS interfaces found ", vdm.getVdmName());
                continue;
            }

            // If the data mover (aka port group) was not previously discovered
            if (portGroup == null) {
                portGroup = new StorageHADomain();
                portGroup.setId(URIUtil.createId(StorageHADomain.class));
                portGroup.setNativeGuid(adapterNativeGuid);
                portGroup.setStorageDeviceURI(system.getId());
                portGroup.setAdapterName(vdm.getVdmName());
                portGroup.setName(vdm.getVdmId());
                portGroup.setFileSharingProtocols(protocols);
                portGroup.setVirtual(true);
                portGroup.setAdapterType(StorageHADomain.HADomainType.VIRTUAL.toString());
                // Get parent Data Mover
                StorageHADomain matchingParentMover = getMatchingMoverById(movers, vdm.getMoverId());
                // Check for valid data mover
                if (null != matchingParentMover) {
                    portGroup.setParentHADomainURI(matchingParentMover.getId());
                } else {
                    _logger.info("Matching parent DataMover {} for {} not found ", vdm.getMoverId(), vdm.getVdmName());
                }
                _logger.info("Found Vdm {} at {}", vdm.getVdmName(), vdm.getVdmId()
                        + "@" + vdm.getMoverId());
                newPortGroups.add(portGroup);
            } else {
                // For rediscovery if cifs is not enabled
                portGroup.setFileSharingProtocols(protocols);
                existingPortGroups.add(portGroup);
            }

            VirtualNAS existingNas = findvNasByNativeId(system, vdm.getVdmId());
            if (existingNas != null) {
                existingNas.setProtocols(protocols);
                existingNas.setCifsServersMap(cifsServersMap);
                existingNas.setNasState(vdm.getState());
                existingNas.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                PhysicalNAS parentNas = findPhysicalNasByNativeId(system, vdm.getMoverId());
                if (parentNas != null) {
                    existingNas.setParentNasUri(parentNas.getId());
                }
                existingNasServers.add(existingNas);
            } else {
                VirtualNAS vNas = createVirtualNas(system, vdm);
                if (vNas != null) {
                    vNas.setProtocols(protocols);
                    vNas.setCifsServersMap(cifsServersMap);
                    newNasServers.add(vNas);
                }
            }
        }

        List<VirtualNAS> discoveredVNasServers = new ArrayList<VirtualNAS>();
        // Persist the NAS servers!!!
        if (existingNasServers != null && !existingNasServers.isEmpty()) {
            _logger.info("discoverVdmPortGroups - modified VirtualNAS servers size {}", existingNasServers.size());
            _dbClient.updateObject(existingNasServers);
            discoveredVNasServers.addAll(existingNasServers);
        }

        if (newNasServers != null && !newNasServers.isEmpty()) {
            _logger.info("discoverVdmPortGroups - new VirtualNAS servers size {}", newNasServers.size());
            _dbClient.createObject(newNasServers);
            discoveredVNasServers.addAll(newNasServers);
        }

        // Verify the existing vnas servers!!!
        DiscoveryUtils.checkVirtualNasNotVisible(discoveredVNasServers, _dbClient, system.getId());

        _logger.info("Vdm Port group discovery for storage system {} complete.", system.getId());
        for (StorageHADomain newDomain : newPortGroups) {
            _logger.debug("New Storage Domain : {} : {}", newDomain.getNativeGuid(), newDomain.getAdapterName() + ":" + newDomain.getId());
        }
        for (StorageHADomain domain : existingPortGroups) {
            _logger.debug("Old Storage Domain : {} : {}", domain.getNativeGuid(), domain.getAdapterName() + ":" + domain.getId());
        }
        // return portGroups;
        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);
        return portGroups;
    }

    /**
     * Retrieve the Data Mover IP Interfaces (aka Storage Ports) for the specified VNX File Storage Array
     * 
     * @param system storage system information including credentials.
     * @return Map of New and Existing Storage Ports
     * @throws VNXFileCollectionException
     * @throws IOException
     */
    private HashMap<String, List<StoragePort>> discoverVdmPorts(StorageSystem system, Set<StorageHADomain> movers)
            throws VNXFileCollectionException, VNXException, IOException {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info("Start storage port discovery for storage system {}", system.getId());

        HashMap<String, VNXDataMoverIntf> vdmIntMap = new HashMap<String, VNXDataMoverIntf>();

        List<VirtualNAS> modifiedServers = new ArrayList<VirtualNAS>();

        // Retrieve VDMs
        List<VNXVdm> vdms = getVdmPortGroups(system);

        // Retrieve the list of data movers interfaces for the VNX File device.
        List<VNXDataMoverIntf> vdmIntfs = getVdmPorts(system, vdms);

        for (VNXDataMoverIntf intf : vdmIntfs) {
            _logger.info("getVdmPorts Adding {} : {}", intf.getName(), intf.getIpAddress());
            vdmIntMap.put(intf.getName(), intf);
        }

        _logger.info("Number VDM mover interfaces found: {}", vdmIntfs.size());

        for (VNXVdm vdm : vdms) {

            List<String> vNasStoragePorts = new ArrayList<String>();
            // Create the list of storage ports.
            for (String vdmIF : vdm.getInterfaces()) {

                VNXDataMoverIntf intf = vdmIntMap.get(vdmIF);

                StoragePort port = null;

                StorageHADomain matchingHADomain = getMatchingMoverByName(movers, vdm.getVdmName());
                // Check for valid data mover
                if (null == matchingHADomain) {
                    continue;
                }

                // Check if storage port was already discovered
                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, intf.getIpAddress(), NativeGUIDGenerator.PORT);

                port = findExistingPort(portNativeGuid);

                // If VDM interface was not previously discovered, add new storage port
                if (port == null) {
                    port = new StoragePort();
                    port.setId(URIUtil.createId(StoragePort.class));
                    port.setLabel(portNativeGuid);
                    port.setTransportType("IP");
                    port.setNativeGuid(portNativeGuid);
                    port.setStorageDevice(system.getId());
                    port.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    port.setPortName(intf.getName());
                    port.setPortNetworkId(intf.getIpAddress());
                    port.setPortGroup(vdm.getVdmId());
                    port.setStorageHADomain(matchingHADomain.getId());
                    _logger.info(
                            "Creating new storage port using NativeGuid : {} name : {}, IP : {}",
                            new Object[] { portNativeGuid, intf.getName(),
                                    intf.getIpAddress(), intf.getDataMoverId(), vdm.getVdmId(), port.getPortName(), port.getPortGroup() });
                    newStoragePorts.add(port);
                } else {
                    port.setStorageHADomain(matchingHADomain.getId());
                    port.setPortGroup(vdm.getVdmId());
                    existingStoragePorts.add(port);
                }
                port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                vNasStoragePorts.add(port.getId().toString());
            }
            // Set storage port details to vNas
            VirtualNAS vNas = findvNasByNativeId(system, vdm.getVdmId());
            if (vNas != null) {
                vNas.getStoragePorts().clear();
                vNas.getStoragePorts().addAll(vNasStoragePorts);
                modifiedServers.add(vNas);
            }
        }

        // Persist the changed nas servers!!!
        if (modifiedServers != null && !modifiedServers.isEmpty()) {
            _logger.info("Modified VirtualNAS servers size {}", modifiedServers.size());
            _dbClient.updateObject(modifiedServers);
        }

        _logger.info("Storage port discovery for storage system {} complete", system.getId());
        for (StoragePort newPort : newStoragePorts) {
            _logger.debug("New Storage Port : {} : {}", newPort.getNativeGuid(), newPort.getPortName() + ":" + newPort.getId());
        }
        for (StoragePort port : existingStoragePorts) {
            _logger.debug("Old Storage Port : {} : {}", port.getNativeGuid(), port.getPortName() + ":" + port.getId());
        }
        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);

        return storagePorts;
    }

    /**
     * 
     * get mover by name
     * 
     * @param movers - list of movers
     * @param moverName - name of mover that need to find out
     * @return
     */
    private StorageHADomain getMatchingMoverByName(Set<StorageHADomain> movers, String moverName) {
        for (StorageHADomain mover : movers) {
            if (mover.getAdapterName().equals(moverName)) {
                return mover;
            }
        }
        return null;
    }

    /**
     * get mover by id
     * 
     * @param movers - list of movers
     * @param moverId - name of the mover id
     * @return
     */
    private StorageHADomain getMatchingMoverById(Set<StorageHADomain> movers, String moverId) {
        for (StorageHADomain mover : movers) {
            if (mover.getName().equals(moverId)) {
                return mover;
            }
        }
        return null;
    }

    /**
     * find existing port using portGuid in ViPR DB
     * 
     * @param portGuid
     * @return
     */
    private StoragePort findExistingPort(String portGuid) {
        URIQueryResultList results = new URIQueryResultList();
        StoragePort port = null;

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portGuid),
                results);
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class, iter.next());

            if (tmpPort != null && !tmpPort.getInactive()) {
                port = tmpPort;
                _logger.info("found port {}", tmpPort.getNativeGuid() + ":" + tmpPort.getPortName());
                break;
            }
        }
        return port;
    }

    /**
     * Discovery of VNX Unmanaged filesystems
     * 
     * @param profile - AccessProfile for the providers
     * @throws BaseCollectionException
     */
    private void discoverUmanagedFileSystems(AccessProfile profile) throws BaseCollectionException {

        _logger.info("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                profile.getIpAddress() + profile.getPortNumber(),
                profile.getnamespace());

        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }
        _logger.info("discoverUmanagedFileSystems - Discovery of VNX Unmanaged filesystems started {}", storageSystem.getIpAddress());

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        int newFileSystemsCount = 0;
        int existingFileSystemsCount = 0;
        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();

        String detailedStatusMessage = "Discovery of VNXFile Unmanaged FileSystem started";

        try {
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);

            HashMap<String, StoragePool> pools = new HashMap<String, StoragePool>();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                if (storagePool != null && !storagePool.getInactive()) {
                    pools.put(storagePool.getNativeId(), storagePool);
                }
            }

            StoragePort storagePort = this.getStoragePortPool(storageSystem);

            List<VNXFileSystem> discoveredFS = discoverAllFileSystems(storageSystem);

            StringSet umfsIds = new StringSet();
            if (!discoveredFS.isEmpty()) {
                for (VNXFileSystem fs : discoveredFS) {

                    if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(fs.getFsName())) {
                        // skipping this file system because the filter doesn't match
                        continue;
                    }

                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), fs.getFsId() + "");
                    StoragePool pool = pools.get(fs.getStoragePool());

                    if (!checkStorageFileSystemExistsInDB(fsNativeGuid)) {
                        // Create UnManaged FS
                        String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(),
                                storageSystem.getSerialNumber().toUpperCase(), fs.getFsId() + "");

                        UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);

                        boolean alreadyExist = unManagedFs == null ? false : true;
                        unManagedFs = createUnManagedFileSystem(unManagedFs, fsUnManagedFsNativeGuid, storageSystem,
                                pool, storagePort, fs);
                        if (alreadyExist) {
                            existingUnManagedFileSystems.add(unManagedFs);
                            existingFileSystemsCount++;
                        } else {
                            unManagedFileSystems.add(unManagedFs);
                            newFileSystemsCount++;
                        }

                        allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());
                        umfsIds.add(fs.getFsId() + "");
                        /**
                         * Persist 200 objects and clear them to avoid memory issue
                         */
                        validateListSizeLimitAndPersist(unManagedFileSystems, existingUnManagedFileSystems,
                                Constants.DEFAULT_PARTITION_SIZE * 2);
                    }

                }
            }

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);
            _logger.info("New unmanaged VNXFile file systems count: {}", newFileSystemsCount);
            _logger.info("Update unmanaged VNXFile file systems count: {}", existingFileSystemsCount);
            if (!unManagedFileSystems.isEmpty()) {
                // Add UnManagedFileSystem
                _dbClient.createObject(unManagedFileSystems);
            }

            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.updateObject(existingUnManagedFileSystems);
            }

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile: %s",
                    storageSystemId.toString());

            if (null != umfsIds && !umfsIds.isEmpty()) {
                // Discovering unmanaged quota directories
                discoverUmanagedFileQuotaDirectory(profile, umfsIds);
                _logger.info(detailedStatusMessage);
            }

        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for VNXFile %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new VNXFileCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Discovery of Vnxfile unmanaged quota directory
     * 
     * @param profile - AccessProfile for the providers
     * @param umfsIds - filesystem ids
     * @throws Exception
     */
    private void discoverUmanagedFileQuotaDirectory(AccessProfile profile, StringSet umfsIds) throws Exception {
        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);

        if (null == storageSystem) {
            return;
        }
        String detailedStatusMessage = "discoverUmanagedFileQuotaDirectory - Discovery of VNX Unmanaged quota directory started";
        _logger.info(detailedStatusMessage);
        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(storageSystem);
            reqAttributeMap.put(VNXFileConstants.CMD_RESULT, VNXFileConstants.CMD_SUCCESS);
            _discExecutor.setKeyMap(reqAttributeMap);

            // Retrieve all the qtree info.
            List<TreeQuota> qtrees = getAllQuotaTrees(storageSystem);
            if (!qtrees.isEmpty()) {
                List<UnManagedFileQuotaDirectory> unManagedFileQuotaDirectories = new ArrayList<>();
                List<UnManagedFileQuotaDirectory> existingUnManagedFileQuotaDirectories = new ArrayList<>();
                String qdName = "";
                for (TreeQuota quotaTree : qtrees) {
                    String fsNativeId;
                    // Process the QD's only of unmanaged file systems.
                    if (!umfsIds.contains(quotaTree.getFileSystem())) {
                        continue;
                    }
                    qdName = "";
                    if (quotaTree.getPath() != null) {
                        // Ignore / from QD path
                        qdName = quotaTree.getPath().substring(1);
                    }

                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), quotaTree.getFileSystem() + "");

                    String nativeGUID = NativeGUIDGenerator.generateNativeGuidForQuotaDir(storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), qdName, quotaTree.getFileSystem() + "");

                    String nativeUnmanagedGUID = NativeGUIDGenerator.generateNativeGuidForUnManagedQuotaDir(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), qdName, quotaTree.getFileSystem() + "");
                    if (checkStorageQuotaDirectoryExistsInDB(nativeGUID)) {
                        continue;
                    }

                    UnManagedFileQuotaDirectory tempUnManagedFileQuotaDirectory = checkUnManagedQuotaDirectoryExistsInDB(
                            nativeUnmanagedGUID);
                    boolean unManagedFileQuotaDirectoryExists = tempUnManagedFileQuotaDirectory == null ? false : true;

                    UnManagedFileQuotaDirectory unManagedFileQuotaDirectory = null;

                    if (!unManagedFileQuotaDirectoryExists) {
                        unManagedFileQuotaDirectory = new UnManagedFileQuotaDirectory();
                        unManagedFileQuotaDirectory.setId(URIUtil.createId(UnManagedFileQuotaDirectory.class));
                    } else {
                        unManagedFileQuotaDirectory = tempUnManagedFileQuotaDirectory;
                    }

                    unManagedFileQuotaDirectory.setLabel(qdName);

                    unManagedFileQuotaDirectory.setNativeGuid(nativeUnmanagedGUID);
                    unManagedFileQuotaDirectory.setParentFSNativeGuid(fsNativeGuid);
                    unManagedFileQuotaDirectory.setOpLock(false);
                    if (quotaTree.getLimits() != null) {
                        /*
                         * response is in MB, so Byte = 1024 * 1024 * response
                         */
                        unManagedFileQuotaDirectory.setSize(
                                Long.valueOf(quotaTree.getLimits().getSpaceHardLimit()) * BYTESCONV * BYTESCONV);
                    }

                    if (!unManagedFileQuotaDirectoryExists) {
                        // Set ID only for new UnManagedQuota Directory
                        unManagedFileQuotaDirectories.add(unManagedFileQuotaDirectory);
                    } else {
                        existingUnManagedFileQuotaDirectories.add(unManagedFileQuotaDirectory);
                    }

                }

                if (!unManagedFileQuotaDirectories.isEmpty()) {
                    _partitionManager.insertInBatches(unManagedFileQuotaDirectories,
                            Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                            UNMANAGED_FILEQUOTADIR);
                }

                if (!existingUnManagedFileQuotaDirectories.isEmpty()) {
                    _partitionManager.updateAndReIndexInBatches(existingUnManagedFileQuotaDirectories,
                            Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                            UNMANAGED_FILEQUOTADIR);
                }
            }

        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discovery of UMFS Quota Directory failed. Storage system: "
                    + storageSystemId);
            throw e;
        }
    }

    /**
     * check the UnManaged quota directory existing in DB
     * 
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    private UnManagedFileQuotaDirectory checkUnManagedQuotaDirectoryExistsInDB(String nativeGuid)
            throws IOException {
        UnManagedFileQuotaDirectory umfsQd = null;
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedFileQuotaDirectoryInfoNativeGUIdConstraint(nativeGuid), result);

        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unManagedFSQDUri = iter.next();
            umfsQd = _dbClient.queryObject(UnManagedFileQuotaDirectory.class, unManagedFSQDUri);

            return umfsQd;
        }
        return umfsQd;
    }

    /**
     * check Storage quotadir exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    private boolean checkStorageQuotaDirectoryExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getQuotaDirsByNativeGuid(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
        }
        return false;
    }

    /**
     * Validate list size and then persist
     * 
     * @param newUnManagedFileSystems
     * @param existingUnManagedFileSystems
     * @param limit
     */
    private void validateListSizeLimitAndPersist(List<UnManagedFileSystem> newUnManagedFileSystems,
            List<UnManagedFileSystem> existingUnManagedFileSystems, int limit) {

        if (newUnManagedFileSystems != null && !newUnManagedFileSystems.isEmpty() && newUnManagedFileSystems.size() >= limit) {
            _dbClient.createObject(newUnManagedFileSystems);
            newUnManagedFileSystems.clear();
        }

        if (existingUnManagedFileSystems != null && !existingUnManagedFileSystems.isEmpty()
                && existingUnManagedFileSystems.size() >= limit) {
            _dbClient.updateObject(existingUnManagedFileSystems);
            existingUnManagedFileSystems.clear();
        }
    }

    // todo -split function COP-32172
    /**
     * Discovery Unmanaged Cifs shares
     * 
     * @param profile - AccessProfile for the providers
     */
    private void discoverUnManagedCifsShares(AccessProfile profile) {

        // Get Storage System
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        String detailedStatusMessage = "Discovery of VNX Unmanaged Shares started";
        _logger.info(detailedStatusMessage);

        // Used to Save the CIFS ACLs to DB
        List<UnManagedCifsShareACL> newUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();
        List<UnManagedCifsShareACL> oldUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();

        try {

            // Discover port groups (data mover ids) and group names (data mover names)
            Set<StorageHADomain> activeDataMovers = discoverActiveDataMovers(storageSystem);

            // Reused from discoverAll
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if (!ports.get(NEW).isEmpty()) {
                _dbClient.createObject(ports.get(NEW));
            }

            List<StoragePort> allPortsList = ports.get(NEW);
            allPortsList.addAll(ports.get(EXISTING));

            Map<String, List<StoragePort>> allPorts = new ConcurrentHashMap<String, List<StoragePort>>();
            for (StoragePort sPort : allPortsList) {
                _logger.debug("DM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                List<StoragePort> spList = allPorts.get(sPort.getStorageHADomain().toString());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);

                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            Map<String, List<StorageHADomain>> allVdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            if (!allVdms.get(NEW).isEmpty()) {
                _dbClient.createObject(allVdms.get(NEW));
            }

            Set<StorageHADomain> allActiveVDMs = new HashSet();
            allActiveVDMs.addAll(allVdms.get(NEW));
            allActiveVDMs.addAll(allVdms.get(EXISTING));

            activeDataMovers.addAll(allVdms.get(NEW));
            activeDataMovers.addAll(allVdms.get(EXISTING));

            Map<String, List<StoragePort>> allVdmPorts = discoverVdmPorts(storageSystem, allActiveVDMs);
            if (!allVdmPorts.get(NEW).isEmpty()) {
                _dbClient.createObject(allVdmPorts.get(NEW));
            }

            List<StoragePort> allVDMPortsList = allVdmPorts.get(NEW);
            allVDMPortsList.addAll(allVdmPorts.get(EXISTING));

            for (StoragePort sPort : allVDMPortsList) {
                List<StoragePort> spList = allPorts.get(sPort.getStorageHADomain().toString());
                _logger.debug("VDM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            List<UnManagedFileSystem> unManagedExportBatch = new ArrayList<UnManagedFileSystem>();

            for (StorageHADomain mover : activeDataMovers) {

                // Get storage port and name for the DM
                if (allPorts.get(mover.getId().toString()) == null || allPorts.get(mover.getId().toString()).isEmpty()) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("No Ports found for {} {}", mover.getName(), mover.getAdapterName());
                    continue;
                } else {
                    _logger.debug("Number of  Ports found for {} : {} ", mover.getName() + ":" + mover.getAdapterName(),
                            allPorts.get(mover.getId().toString()).size());
                }
                Collections.shuffle(allPorts.get(mover.getId().toString()));
                StoragePort storagePort = allPorts.get(mover.getId().toString()).get(0);
                if (storagePort == null) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("StoragePort is null");
                    continue;
                }
                // storagePort.setStorageHADomain(mover.getId());

                // get vnas uri
                URI moverURI = getNASUri(mover, storageSystem);

                // Retrieve FS-mountpath map for the Data Mover.
                _logger.info("Retrieving FS-mountpath map for Data Mover {}.",
                        mover.getAdapterName());
                VNXFileSshApi sshDmApi = new VNXFileSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(),
                        storageSystem.getPassword());

                Map<String, String> fileSystemMountpathMap = sshDmApi.getFsMountpathMap(
                        mover.getAdapterName());

                Map<String, Map<String, String>> moverExportDetails = sshDmApi.getCIFSExportsForPath(mover.getAdapterName());

                Map<String, String> nameIdMap = getFsNameFsNativeIdMap(storageSystem);

                // Loop through the map and, if the file exists in DB, retrieve the
                // export, process export, and associate export with the FS
                Set<String> fsNames = fileSystemMountpathMap.keySet();
                for (String fsName : fsNames) {
                    // Retrieve FS from DB. If FS found, retrieve export and process
                    String fsMountPath = fileSystemMountpathMap.get(fsName);

                    // Get FS ID for nativeGUID
                    // VNXFileSystem vnxFileSystems = discoverNamedFileSystem(storageSystem, fsName);
                    String fsId = nameIdMap.get(fsName);
                    _logger.debug("Resolved FileSystem name {} to native Id {}",
                            fsName, fsId);

                    UnManagedFileSystem vnxufs = null;
                    if (fsId != null) {
                        String fsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                fsId);

                        vnxufs = checkUnManagedFileSystemExistsInDB(fsNativeGuid);
                    }

                    if (vnxufs != null) {
                        int noOfShares = 0;
                        // Get export info
                        for (String expPath : moverExportDetails.keySet()) {
                            if (!expPath.contains(fsMountPath)) {
                                // Ignore this path as it is not among the exports
                                continue;
                            } else {
                                // We should process only FS and its sub-directory exports only.
                                String subDir = expPath.substring(fsMountPath.length());
                                if (!subDir.isEmpty() && !subDir.startsWith("/")) {
                                    continue;
                                }
                                _logger.info("Path : {} ", expPath);
                            }
                            Map<String, String> fsExportInfo = moverExportDetails.get(expPath);
                            if ((fsExportInfo != null) && (fsExportInfo.size() > 0)) {
                                noOfShares += 1;
                                _logger.info("Associating FS share map for VNX UMFS {}",
                                        vnxufs.getLabel());

                                associateCifsExportWithFS(vnxufs, expPath, fsExportInfo,
                                        storagePort);
                                vnxufs.setHasShares(true);
                                vnxufs.putFileSystemCharacterstics(
                                        UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                                .toString(),
                                        TRUE);

                                _logger.debug("Export map for VNX UMFS {} = {}",
                                        vnxufs.getLabel(), vnxufs.getUnManagedSmbShareMap());

                                List<UnManagedCifsShareACL> cifsACLs = applyCifsSecurityRules(vnxufs, expPath, fsExportInfo, storagePort);

                                _logger.info("Number of acls discovered for file system {} is {}",
                                        vnxufs.getId() + ":" + vnxufs.getLabel(), cifsACLs.size());

                                for (UnManagedCifsShareACL cifsAcl : cifsACLs) {

                                    _logger.info("Unmanaged File share acl: {}", cifsAcl);
                                    String fsShareNativeId = cifsAcl.getFileSystemShareACLIndex();
                                    _logger.info("UMFS Share ACL index: {}", fsShareNativeId);
                                    String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                            .generateNativeGuidForPreExistingFileShare(
                                                    storageSystem, fsShareNativeId);
                                    _logger.info("Native GUID {}", fsUnManagedFileShareNativeGuid);
                                    cifsAcl.setNativeGuid(fsUnManagedFileShareNativeGuid);

                                    // Check whether the CIFS share ACL was present in ViPR DB.
                                    UnManagedCifsShareACL existingACL = checkUnManagedFsCifsACLExistsInDB(_dbClient,
                                            cifsAcl.getNativeGuid());
                                    if (existingACL == null) {
                                        newUnManagedCifsACLs.add(cifsAcl);
                                    } else {
                                        newUnManagedCifsACLs.add(cifsAcl);
                                        existingACL.setInactive(true);
                                        oldUnManagedCifsACLs.add(existingACL);
                                    }

                                }

                                // set vNAS on umfs
                                StringSet moverSet = new StringSet();
                                moverSet.add(moverURI.toString());
                                vnxufs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.NAS.toString(), moverSet);

                                unManagedExportBatch.add(vnxufs);

                            }
                        }

                        if (noOfShares == 0) {
                            _logger.info("FileSystem {} does not have shares ", vnxufs.getLabel());
                        }
                    }

                    if (!unManagedExportBatch.isEmpty() && unManagedExportBatch.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                        // Add UnManagedFileSystem batch
                        // Update UnManagedFilesystem
                        _dbClient.updateObject(unManagedExportBatch);
                        unManagedExportBatch.clear();
                    }

                    if (!newUnManagedCifsACLs.isEmpty() && newUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                        // create new UnManagedCifsShareACL
                        _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
                        _dbClient.createObject(newUnManagedCifsACLs);
                        newUnManagedCifsACLs.clear();
                    }

                    if (!oldUnManagedCifsACLs.isEmpty() && oldUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                        // Update existing UnManagedCifsShareACL
                        _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
                        _dbClient.updateObject(oldUnManagedCifsACLs);
                        oldUnManagedCifsACLs.clear();
                    }
                }
            }

            if (!unManagedExportBatch.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.updateObject(unManagedExportBatch);
                unManagedExportBatch.clear();
            }

            if (!newUnManagedCifsACLs.isEmpty()) {
                // create new UnManagedCifsShareACL
                _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
                _dbClient.createObject(newUnManagedCifsACLs);
                newUnManagedCifsACLs.clear();
            }

            if (!oldUnManagedCifsACLs.isEmpty()) {
                // Update existing UnManagedCifsShareACL
                _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
                _dbClient.updateObject(oldUnManagedCifsACLs);
                oldUnManagedCifsACLs.clear();
            }

            // discovery succeeds
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile shares: %s",
                    storageSystemId.toString());

        } catch (Exception ex) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                            .toString());
            detailedStatusMessage = String.format("Discovery failed for VNXFile cifs shares %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _logger.error(detailedStatusMessage, ex);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    /**
     * get the nas or vnas uri
     * 
     * @param storageHADomain
     * @param storageSystem
     * @return
     */
    private URI getNASUri(StorageHADomain storageHADomain, StorageSystem storageSystem) {
        URI moverURI = null;
        if (storageHADomain.getVirtual() == true) {
            VirtualNAS virtualNAS = findvNasByNativeId(storageSystem, storageHADomain.getName());
            if (virtualNAS != null) {
                moverURI = virtualNAS.getId();
            }
        } else {
            PhysicalNAS physicalNAS = findPhysicalNasByNativeId(storageSystem, storageHADomain.getName());
            if (physicalNAS != null) {
                moverURI = physicalNAS.getId();
            }
        }
        return moverURI;
    }

    /**
     * prepare the UnManagedCifsShare ACL
     * 
     * @param vnxufs - unmanaged object
     * @param expPath -export path
     * @param fsExportInfo - map
     * @param storagePort
     * @return
     */
    private List<UnManagedCifsShareACL> applyCifsSecurityRules(UnManagedFileSystem vnxufs, String expPath,
            Map<String, String> fsExportInfo, StoragePort storagePort) {

        List<UnManagedCifsShareACL> cifsACLs = new ArrayList<UnManagedCifsShareACL>();

        UnManagedCifsShareACL unManagedCifsShareACL = new UnManagedCifsShareACL();
        String shareName = fsExportInfo.get(VNXFileConstants.SHARE_NAME);

        unManagedCifsShareACL.setShareName(shareName);

        // user
        unManagedCifsShareACL.setUser(FileControllerConstants.CIFS_SHARE_USER_EVERYONE);
        // permission
        unManagedCifsShareACL.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);

        unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));

        // filesystem id
        unManagedCifsShareACL.setFileSystemId(vnxufs.getId());

        cifsACLs.add(unManagedCifsShareACL);

        return cifsACLs;

    }

    /**
     * Discovery of Unmanaged new exports
     * 
     * @param profile - AccessProfile for the providers
     */
    private void discoverUnmanagedNewExports(AccessProfile profile) {

        // Get Storage System
        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        String detailedStatusMessage = "Discovery of VNX Unmanaged Exports started";
        _logger.info(detailedStatusMessage);

        // Used to Save the rules to DB
        List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();
        List<UnManagedFileExportRule> oldUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();

        try {

            // Verification Utility
            UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
                    _dbClient);

            // Discover port groups (data mover ids) and group names (data mover names)
            Set<StorageHADomain> activeDataMovers = discoverActiveDataMovers(storageSystem);

            // Reused from discoverAll
            // Discover ports (data mover interfaces) with the data movers in the active set.
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, activeDataMovers);

            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if (!ports.get(NEW).isEmpty()) {
                _dbClient.createObject(ports.get(NEW));
            }

            List<StoragePort> allPortsList = ports.get(NEW);
            allPortsList.addAll(ports.get(EXISTING));

            Map<String, List<StoragePort>> allPorts = new ConcurrentHashMap<String, List<StoragePort>>();
            for (StoragePort sPort : allPortsList) {
                _logger.debug("DM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                List<StoragePort> spList = allPorts.get(sPort.getStorageHADomain().toString());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            Map<String, List<StorageHADomain>> allVdms = discoverVdmPortGroups(storageSystem, activeDataMovers);
            if (!allVdms.get(NEW).isEmpty()) {
                _dbClient.createObject(allVdms.get(NEW));
            }

            Set<StorageHADomain> allActiveVDMs = new HashSet();
            allActiveVDMs.addAll(allVdms.get(NEW));
            allActiveVDMs.addAll(allVdms.get(EXISTING));

            activeDataMovers.addAll(allVdms.get(NEW));
            activeDataMovers.addAll(allVdms.get(EXISTING));

            Map<String, List<StoragePort>> allVdmPorts = discoverVdmPorts(storageSystem, allActiveVDMs);
            if (!allVdmPorts.get(NEW).isEmpty()) {
                _dbClient.createObject(allVdmPorts.get(NEW));
            }

            List<StoragePort> allVDMPortsList = allVdmPorts.get(NEW);
            allVDMPortsList.addAll(allVdmPorts.get(EXISTING));

            for (StoragePort sPort : allVDMPortsList) {
                List<StoragePort> spList = allPorts.get(sPort.getStorageHADomain().toString());
                _logger.debug("VDM Storage Port  {}  StorageHADomain {}", sPort.getPortNetworkId(), sPort.getStorageHADomain());
                if (spList == null) {
                    spList = new ArrayList<>();
                }
                spList.add(sPort);
                allPorts.put(sPort.getStorageHADomain().toString(), spList);
            }

            List<UnManagedFileSystem> unManagedExportBatch = new ArrayList<>();

            for (StorageHADomain mover : activeDataMovers) {

                // Get storage port and name for the DM
                if (allPorts.get(mover.getId().toString()) == null || allPorts.get(mover.getId().toString()).isEmpty()) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("No Ports found for {} {}", mover.getName(), mover.getAdapterName());
                    continue;
                } else {
                    _logger.debug("Number of  Ports found for {} : {} ", mover.getName() + ":" + mover.getAdapterName(),
                            allPorts.get(mover.getId().toString()).size());
                }
                Collections.shuffle(allPorts.get(mover.getId().toString()));
                StoragePort storagePort = allPorts.get(mover.getId().toString()).get(0);
                if (storagePort == null) {
                    // Did not find a single storage port for this DM, ignore it
                    _logger.debug("StoragePort is null");
                    continue;
                }
                // storagePort.setStorageHADomain(mover.getId());
                // get vnas uri
                URI moverURI = getNASUri(mover, storageSystem);
                // Retrieve FS-mountpath map for the Data Mover.
                _logger.info("Retrieving FS-mountpath map for Data Mover {}.",
                        mover.getAdapterName());
                VNXFileSshApi sshDmApi = new VNXFileSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(),
                        storageSystem.getPassword());

                Map<String, String> fileSystemMountpathMap = sshDmApi.getFsMountpathMap(
                        mover.getAdapterName());

                Map<String, Map<String, String>> moverExportDetails = sshDmApi.getNFSExportsForPath(mover.getAdapterName());

                Map<String, String> nameIdMap = getFsNameFsNativeIdMap(storageSystem);

                // Loop through the map and, if the file exists in DB, retrieve the
                // export, process export, and associate export with the FS
                Set<String> fsNames = fileSystemMountpathMap.keySet();
                for (String fsName : fsNames) {
                    // Retrieve FS from DB. If FS found, retrieve export and process
                    String fsMountPath = fileSystemMountpathMap.get(fsName);

                    // Get FS ID for nativeGUID
                    // VNXFileSystem vnxFileSystems = discoverNamedFileSystem(storageSystem, fsName);
                    String fsId = nameIdMap.get(fsName);
                    _logger.debug("Resolved FileSystem name {} to native Id {}", fsName, fsId);

                    UnManagedFileSystem vnxufs = null;
                    if (fsId != null) {
                        String fsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                fsId);

                        vnxufs = checkUnManagedFileSystemExistsInDB(fsNativeGuid);
                    }

                    if (vnxufs != null) {
                        // Get export info
                        int noOfExports = 0;
                        boolean inValidExports = false;
                        for (String expPath : moverExportDetails.keySet()) {
                            if (!expPath.contains(fsMountPath)) {
                                // Ingore this path as it is not among the exports
                                continue;
                            } else {
                                // We should process only FS and its sub-directory exports only.
                                String subDir = expPath.substring(fsMountPath.length());
                                if (!subDir.isEmpty() && !subDir.startsWith("/")) {
                                    continue;
                                }
                                _logger.info("Path : {} ", expPath);
                                noOfExports++;
                            }

                            // Used as for rules validation
                            List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();

                            Map<String, String> fsExportInfo = moverExportDetails.get(expPath);
                            if (fsExportInfo != null && !fsExportInfo.isEmpty()) {
                                // If multiple security flavors, do not add to ViPR DB
                                String securityFlavors = fsExportInfo.get(VNXFileConstants.SECURITY_TYPE);
                                if (securityFlavors == null || securityFlavors.length() == 0) {
                                    securityFlavors = "sys";
                                }
                                if (securityFlavors != null) {

                                    String fsMountPoint = storagePort.getPortNetworkId() + ":" + expPath;

                                    _logger.info("Associating FS export map for VNX UMFS {}", vnxufs.getLabel());

                                    associateExportWithFS(vnxufs, expPath, fsExportInfo, expPath,
                                            storagePort);

                                    _logger.debug("Export map for VNX UMFS {} = {}", vnxufs.getLabel(), vnxufs.getFsUnManagedExportMap());

                                    List<UnManagedFileExportRule> exportRules = applyAllSecurityRules(vnxufs.getId(), expPath,
                                            fsMountPoint, securityFlavors,
                                            fsExportInfo);
                                    _logger.info("Number of export rules discovered for file system {} is {}", vnxufs.getId() + ":"
                                            + vnxufs.getLabel(), exportRules.size());
                                    if (!exportRules.isEmpty()) {
                                        for (UnManagedFileExportRule dbExportRule : exportRules) {
                                            _logger.info("Unmanaged File Export Rule : {}", dbExportRule);
                                            String fsExportRulenativeId = dbExportRule.getFsExportIndex();
                                            _logger.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
                                            String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                                                    .generateNativeGuidForPreExistingFileExportRule(
                                                            storageSystem, fsExportRulenativeId);
                                            _logger.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);

                                            dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                                            dbExportRule.setFileSystemId(vnxufs.getId());
                                            dbExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                                            // Build all export rules list.
                                            unManagedExportRules.add(dbExportRule);
                                        }
                                    }

                                    // Validate Rules Compatible with ViPR - Same rules should
                                    // apply as per API SVC Validations.
                                    if (!unManagedExportRules.isEmpty()) {
                                        boolean isAllRulesValid = validationUtility
                                                .validateUnManagedExportRules(unManagedExportRules, false);
                                        if (isAllRulesValid) {
                                            _logger.info("Validating rules success for export {}", expPath);
                                            for (UnManagedFileExportRule exportRule : unManagedExportRules) {
                                                UnManagedFileExportRule existingRule = checkUnManagedFsExportRuleExistsInDB(_dbClient,
                                                        exportRule.getNativeGuid());
                                                if (existingRule == null) {
                                                    newUnManagedExportRules.add(exportRule);
                                                } else {
                                                    // Remove the existing rule.
                                                    existingRule.setInactive(true);
                                                    _dbClient.updateObject(existingRule);
                                                    newUnManagedExportRules.add(exportRule);
                                                }
                                            }
                                            vnxufs.setHasExports(true);
                                            vnxufs.putFileSystemCharacterstics(
                                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                                            .toString(),
                                                    TRUE);

                                            // Set the correct storage port
                                            if (null != storagePort) {
                                                StringSet storagePorts = new StringSet();
                                                storagePorts.add(storagePort.getId().toString());
                                                vnxufs.getFileSystemInformation().put(
                                                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(),
                                                        storagePorts);
                                            }
                                            _dbClient.updateObject(vnxufs);
                                            _logger.info("File System {} has Exports and their size is {}", vnxufs.getId(),
                                                    newUnManagedExportRules.size());
                                        } else {
                                            _logger.warn(
                                                    "Validating rules failed for export {}. Ignoring to import these rules into ViPR DB",
                                                    vnxufs);
                                            inValidExports = true;
                                        }
                                    } else {
                                        _logger.warn("Export discovery failed for  {}. Ignoring to import these rules into ViPR DB",
                                                vnxufs);
                                        inValidExports = true;
                                    }
                                    // Adding this additional logic to avoid OOM
                                    if (newUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                                        _logger.info("Saving Number of New UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                                        _dbClient.createObject(newUnManagedExportRules);
                                        newUnManagedExportRules.clear();
                                    }

                                    // Adding this additional logic to avoid OOM
                                    if (oldUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                                        _logger.info("Saving Number of Existing UnManagedFileExportRule(s) {}",
                                                oldUnManagedExportRules.size());
                                        _dbClient.updateObject(oldUnManagedExportRules);
                                        oldUnManagedExportRules.clear();
                                    }
                                }
                            }
                        }
                        _logger.info("No of exports found for path {} = {} ", fsMountPath, noOfExports);

                        if (noOfExports == 0) {
                            _logger.info("FileSystem {} does not have any exports ", vnxufs.getLabel());
                            vnxufs.setHasExports(false);
                        }
                        // Don't consider the unmanaged file systems with invalid exports!!!
                        if (inValidExports) {
                            _logger.info("Ignoring unmanaged file system {}, due to invalid exports", vnxufs.getLabel());
                            vnxufs.setInactive(true);
                        }

                        // set the vNAS uri in umfs
                        StringSet moverSet = new StringSet();
                        moverSet.add(moverURI.toString());
                        vnxufs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.NAS.toString(), moverSet);
                        _logger.info("nas server id {} and fs name {}", mover.getName(), fsName);

                        unManagedExportBatch.add(vnxufs);

                        if (!unManagedExportBatch.isEmpty() && unManagedExportBatch.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                            // Add UnManagedFileSystem batch
                            // Update UnManagedFilesystem
                            _dbClient.updateObject(unManagedExportBatch);
                            unManagedExportBatch.clear();
                        }
                    }
                }
            }

            if (!unManagedExportBatch.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.updateObject(unManagedExportBatch);
                unManagedExportBatch.clear();
            }

            if (!newUnManagedExportRules.isEmpty()) {
                // create new UnManagedExportFules
                _logger.info("Saving Number of New UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                _dbClient.createObject(newUnManagedExportRules);
                newUnManagedExportRules.clear();
            }

            if (!oldUnManagedExportRules.isEmpty()) {
                // Update exisiting UnManagedExportFules
                _logger.info("Saving Number of Old UnManagedFileExportRule(s) {}", oldUnManagedExportRules.size());
                _dbClient.updateObject(oldUnManagedExportRules);
                oldUnManagedExportRules.clear();
            }

            // discovery succeeds
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            detailedStatusMessage = String.format("Discovery completed successfully for VNXFile export: %s",
                    storageSystemId.toString());

        } catch (Exception ex) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                            .toString());
            detailedStatusMessage = String.format("Discovery failed for VNXFile exports %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _logger.error(detailedStatusMessage, ex);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    /**
     * prepare the UnManagedFileExport rule from fsexportInfo
     * 
     * @param id
     * @param exportPath -export path
     * @param mountPoint - mount point
     * @param secFlavors -security flavors
     * @param fsExportInfo
     * @return
     */
    private List<UnManagedFileExportRule> applyAllSecurityRules(URI id, String exportPath, String mountPoint,
            String secFlavors, Map<String, String> fsExportInfo) {

        List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();

        String[] secs = secFlavors.split(
                VNXFileConstants.SECURITY_SEPARATORS);

        for (String sec : secs) {

            String anonUser = fsExportInfo.get(VNXFileConstants.ANON);
            StringSet readOnlyHosts = null;
            StringSet readWriteHosts = null;
            StringSet rootHosts = null;
            StringSet accessHosts = null;
            String hosts = null;

            hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                accessHosts = new StringSet();
                accessHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }

            hosts = fsExportInfo.get(VNXFileConstants.RO);
            if (hosts != null) {
                readOnlyHosts = new StringSet();
                readOnlyHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));

            }
            hosts = fsExportInfo.get(VNXFileConstants.RW);
            if (hosts != null) {
                readWriteHosts = new StringSet();
                readWriteHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }

            hosts = fsExportInfo.get(VNXFileConstants.ROOT);
            if (hosts != null) {
                rootHosts = new StringSet();
                rootHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }

            hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                if (readWriteHosts == null) {
                    readWriteHosts = new StringSet();
                }
                readWriteHosts.addAll(new HashSet<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS))));
            }

            UnManagedFileExportRule unManagedfileExportRule = createUnManagedExportRule(id, exportPath,
                    mountPoint, sec, anonUser, accessHosts, readOnlyHosts, readWriteHosts, rootHosts);

            expRules.add(unManagedfileExportRule);

        } // end of for loop

        return expRules;
    }

    /**
     * discovery active data movers of vnxfile
     * 
     * @param storageSystem
     * @return
     */
    private Set<StorageHADomain> discoverActiveDataMovers(StorageSystem storageSystem) {

        // Reused from discoverAll
        Set<StorageHADomain> activeDataMovers = new HashSet<StorageHADomain>();
        StringSet fileSharingProtocols = new StringSet();
        Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, fileSharingProtocols);
        _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
        _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
        if (!groups.get(NEW).isEmpty()) {
            _dbClient.createObject(groups.get(NEW));
        }

        // Keep a set of active data movers. Data movers in 'standby' state are not added to the
        // database since they cannot be used in this state.
        List<StorageHADomain> newStorageDomains = groups.get(NEW);
        for (StorageHADomain mover : newStorageDomains) {
            activeDataMovers.add(mover);
        }
        List<StorageHADomain> existingStorageDomains = groups.get(EXISTING);
        for (StorageHADomain mover : existingStorageDomains) {
            activeDataMovers.add(mover);
        }

        for (StorageHADomain mover : activeDataMovers) {
            _logger.info("DataMover {} : {}", mover.getName(), mover.getAdapterName());
        }
        return activeDataMovers;
    }

    /**
     * Retrieve the FileSystem for the specified VNX File Storage Array
     * 
     * @param system storage system information including credentials.
     * @return list of Storage FileSystems
     * @throws VNXFileCollectionException
     */
    private List<VNXFileSystem> discoverAllFileSystems(StorageSystem system)
            throws VNXFileCollectionException, VNXException {

        List<VNXFileSystem> fileSystems = new ArrayList<VNXFileSystem>();

        _logger.info("Start FileSystem discovery for storage system {}", system.getId());
        try {
            // Retrieve the list of FileSystem for the VNX File device.

            List<VNXFileSystem> vnxFileSystems = getAllFileSystem(system);
            _logger.info("Number filesytems found: {}", vnxFileSystems.size());
            if (vnxFileSystems != null) {
                // Create the list of FileSystem.
                for (VNXFileSystem vnxfs : vnxFileSystems) {

                    FileShare fs = null;

                    // Check if FileSystem was already discovered
                    URIQueryResultList results = new URIQueryResultList();
                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            system, vnxfs.getFsId() + "", NativeGUIDGenerator.FILESYSTEM);

                    if (checkStorageFileSystemExistsInDB(fsNativeGuid)) {
                        continue;
                    }

                    vnxfs.setFsNativeGuid(fsNativeGuid);
                    fileSystems.add(vnxfs);
                }
            }
            _logger.info("Number of FileSystem found {} and they are : ", fileSystems.size());

        } catch (IOException e) {
            _logger.error("I/O Exception: Discovery of FileSystem failed for storage system {} for {}",
                    system.getId(), e.getMessage());

            VNXFileCollectionException vnxe = new VNXFileCollectionException(
                    "Storage FileSystem discovery error for storage system " + system.getId());
            vnxe.initCause(e);

            throw vnxe;
        }

        _logger.info("Storage FilesSystem discovery for storage system {} complete", system.getId());
        return fileSystems;
    }

    /**
     * If discovery fails, then mark the system as unreachable. The
     * discovery framework will remove the storage system from the database.
     * 
     * @param system the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.updateObject(system);
        } catch (DatabaseException e) {
            _logger.error("discoverStorage failed.  Failed to update discovery status to ERROR.", e);
        }

    }

    /**
     * get the storage pool from vnx file system
     * 
     * @param system
     * @return
     * @throws VNXException
     */
    private List<VNXStoragePool> getStoragePools(final StorageSystem system)
            throws VNXException {

        List<VNXStoragePool> storagePools = null;
        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _logger.info("{}", _discExecutor);

            _discExecutor.setKeyMap(reqAttributeMap);
            _logger.info("{}", _discNamespaces.getNsList().get(
                    "vnxfileStoragePool"));
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePool"));
            storagePools = (ArrayList<VNXStoragePool>) _discExecutor
                    .getKeyMap().get(VNXFileConstants.STORAGEPOOLS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get control station op failed", e);
        }
        return storagePools;
    }

    private VNXControlStation getControlStation(final StorageSystem system)
            throws VNXException {

        VNXControlStation station = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileControlStation"));

            station = (VNXControlStation) _discExecutor.getKeyMap().get(
                    VNXFileConstants.CONTROL_STATION_INFO);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get control station op failed", e);
        }

        return station;
    }

    /**
     * get port groups from vnxfile
     * 
     * @param system
     * @return
     * @throws VNXException
     */
    private List<VNXDataMover> getPortGroups(final StorageSystem system)
            throws VNXException {

        List<VNXDataMover> dataMovers = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePortGroup"));
            dataMovers = (ArrayList<VNXDataMover>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORT_GROUPS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Port Groups op failed", e);
        }

        return dataMovers;
    }

    /**
     * call to check Cifs enabled
     * 
     * @param system
     * @param mover
     * @return
     * @throws VNXException
     */
    private boolean checkCifsEnabled(final StorageSystem system, VNXDataMover mover) throws VNXException {
        boolean cifsSupported = false;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            reqAttributeMap.put(VNXFileConstants.MOVER_ID, Integer.toString(mover.getId()));
            reqAttributeMap.put(VNXFileConstants.ISVDM, "false");

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsSupported = (Boolean) _discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SUPPORTED);

        } catch (BaseCollectionException e) {
            throw new VNXException("check CIFS Enabled op failed", e);
        }

        return cifsSupported;
    }

    /**
     * get cifs server details from vnxfile system
     * 
     * @param system
     * @param moverId
     * @param isVdm
     * @return
     * @throws VNXException
     */
    private List<VNXCifsServer> getCifServers(final StorageSystem system, String moverId, String isVdm) throws VNXException {
        List<VNXCifsServer> cifsServers = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            reqAttributeMap.put(VNXFileConstants.MOVER_ID, moverId);
            reqAttributeMap.put(VNXFileConstants.ISVDM, isVdm);

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsServers = (List<VNXCifsServer>) _discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);

        } catch (BaseCollectionException e) {
            throw new VNXException("Get CifServers op failed", e);
        }

        return cifsServers;
    }

    /**
     * get cifs server details from vnxfile system
     * 
     * @param system
     * @return
     * @throws VNXException
     */
    private List<VNXCifsServer> getCifServers(final StorageSystem system) throws VNXException {
        List<VNXCifsServer> cifsServers;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);

            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(VNXFileConstants.VNX_FILE_CIFS_CONFIG));

            cifsServers = (List<VNXCifsServer>) _discExecutor.getKeyMap().get(VNXFileConstants.CIFS_SERVERS);

        } catch (BaseCollectionException e) {
            throw new VNXException("Get CifServers op failed", e);
        }

        return cifsServers;
    }

    /**
     * get the port from vnxfile system
     * 
     * @param system
     * @return
     * @throws VNXException
     */
    private List<VNXDataMoverIntf> getPorts(final StorageSystem system)
            throws VNXException {

        List<VNXDataMoverIntf> dataMovers = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePort"));
            dataMovers = (ArrayList<VNXDataMoverIntf>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORTS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Port op failed", e);
        }

        return dataMovers;
    }

    /**
     * get the vdm ports group from vnxfile
     * 
     * @param system
     * @return
     * @throws VNXException
     */
    private List<VNXVdm> getVdmPortGroups(final StorageSystem system)
            throws VNXException {

        List<VNXVdm> vdms = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileVdm"));
            vdms = (ArrayList<VNXVdm>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.VDM_INFO);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get Vdm Port Groups op failed", e);
        }

        return vdms;
    }

    /**
     * get the vdm ports from device
     * 
     * @param system
     * @param vdms
     * @return
     * @throws VNXException
     */
    private List<VNXDataMoverIntf> getVdmPorts(final StorageSystem system, final List<VNXVdm> vdms)
            throws VNXException {

        List<VNXDataMoverIntf> dataMoverInterfaces = null;
        List<VNXDataMoverIntf> vdmInterfaces = new ArrayList<VNXDataMoverIntf>();
        Map<String, VNXDataMoverIntf> dmIntMap = new HashMap<String, VNXDataMoverIntf>();

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileStoragePort"));
            dataMoverInterfaces = (ArrayList<VNXDataMoverIntf>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.STORAGE_PORTS);
            // Make map
            for (VNXDataMoverIntf intf : dataMoverInterfaces) {
                dmIntMap.put(intf.getName(), intf);
            }

            VNXFileSshApi sshDmApi = new VNXFileSshApi();
            sshDmApi.setConnParams(system.getIpAddress(), system.getUsername(), system.getPassword());

            // collect VDM interfaces
            for (VNXVdm vdm : vdms) {
                for (String vdmIF : vdm.getInterfaces()) {
                    VNXDataMoverIntf vdmInterface = dmIntMap.get(vdmIF);
                    vdmInterfaces.add(vdmInterface);
                    _logger.info("Use this VDM interface {}", vdmIF);
                }
                // Collect NFS/CIFS interfaces from nas_server -info command. This will return
                // Interfaces assigned to VDM and not thru CIFS servers
                Map<String, String> vdmIntfs = sshDmApi.getVDMInterfaces(vdm.getVdmName());
                for (String vdmNFSIf : vdmIntfs.keySet()) {
                    VNXDataMoverIntf vdmInterface = dmIntMap.get(vdmNFSIf);
                    if (vdmInterface != null) {
                        _logger.info("Use this NFS VDM interface {} for {}", vdmInterface, vdmNFSIf);
                        vdmInterfaces.add(vdmInterface);
                        // Check if the interface is already on the VDM, if not, add it.
                        if (!vdm.getInterfaces().contains(vdmInterface.getName())) {
                            vdm.getInterfaces().add(vdmInterface.getName());
                        }
                    } else {
                        _logger.info("No interface found for {}", vdmNFSIf);
                    }
                }
            }

        } catch (BaseCollectionException e) {
            throw new VNXException("Get VDM Port op failed", e);
        }

        return vdmInterfaces;
    }

    /**
     * call to get all vnxfile system from Device
     * 
     * @param system - vnx storagesystem object
     * @return
     * @throws VNXException
     */
    private List<VNXFileSystem> getAllFileSystem(final StorageSystem system)
            throws VNXException {

        List<VNXFileSystem> fileSystems = null;

        try {
            Map<String, Object> reqAttributeMap = getRequestParamsMap(system);
            _discExecutor.setKeyMap(reqAttributeMap);
            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxfileSystem"));

            fileSystems = (ArrayList<VNXFileSystem>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.FILESYSTEMS);
        } catch (BaseCollectionException e) {
            throw new VNXException("Get FileSystems op failed", e);
        }

        return fileSystems;
    }

    /**
     * call to get all quota tree from storage system
     * 
     * @param system - vnx storagesystem object
     * @return
     * @throws VNXException
     */
    private List<TreeQuota> getAllQuotaTrees(final StorageSystem system)
            throws VNXException {

        List<TreeQuota> quotaTrees = new ArrayList<TreeQuota>();
        List<TreeQuota> tempQuotaTrees = null;

        try {

            _discExecutor.execute((Namespace) _discNamespaces.getNsList().get(
                    "vnxallquotas"));

            tempQuotaTrees = (ArrayList<TreeQuota>) _discExecutor.getKeyMap()
                    .get(VNXFileConstants.QUOTA_DIR_LIST);

            if (null != tempQuotaTrees && !tempQuotaTrees.isEmpty()) {
                quotaTrees.addAll(tempQuotaTrees);
                _logger.info("Found {} Quota directories ", tempQuotaTrees.size());
            } else {
                _logger.info("No Quota directories found ");
            }
        } catch (BaseCollectionException e) {
            throw new VNXException("Get QuotaTrees op failed", e);
        }

        return quotaTrees;
    }

    /**
     * associate nfs export with unmanaged filesystem
     * 
     * @param vnxufs - unmanaged vnx file object
     * @param exportPath - export path
     * @param fsExportInfo - map contains export details.
     * @param mountPath - mount path of the filesystem
     * @param storagePort - storage port on which filesystem access
     */
    private void associateExportWithFS(UnManagedFileSystem vnxufs,
            String exportPath, Map<String, String> fsExportInfo, String mountPath, StoragePort storagePort) {

        try {
            String security = fsExportInfo.get(VNXFileConstants.SECURITY_TYPE);
            if (security == null) {
                security = FileShareExport.SecurityTypes.sys.toString();
            } else {
                String[] securityFlavorArr = security.split(
                        VNXFileConstants.SECURITY_SEPARATORS);
                if (securityFlavorArr.length == 0) {
                    security = FileShareExport.SecurityTypes.sys.toString();
                } else {
                    security = securityFlavorArr[0];
                }
            }

            // Assign storage port to unmanaged FS
            vnxufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
            if (storagePort != null && storagePort.getId() != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                vnxufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }

            // Get protocol, NFS by default
            String protocol = fsExportInfo.get(VNXFileConstants.PROTOCOL);
            if (protocol == null) {
                protocol = StorageProtocol.File.NFS.toString();
            }

            List<String> accessHosts = null;
            List<String> roHosts = null;
            List<String> rwHosts = null;
            List<String> rootHosts = null;

            // TODO all hosts
            String hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                accessHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
            }
            hosts = fsExportInfo.get(VNXFileConstants.RO);
            if (hosts != null) {
                roHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
                ;
            }
            hosts = fsExportInfo.get(VNXFileConstants.RW);
            if (hosts != null) {
                rwHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
                ;
            }
            hosts = fsExportInfo.get(VNXFileConstants.ACCESS);
            if (hosts != null) {
                if (rwHosts == null) {
                    rwHosts = new ArrayList();
                }
                rwHosts.addAll(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
            }
            hosts = fsExportInfo.get(VNXFileConstants.ROOT);
            if (hosts != null) {
                rootHosts = new ArrayList<String>(Arrays.asList(hosts.split(
                        VNXFileConstants.HOST_SEPARATORS)));
                ;
            }
            String anonUser = fsExportInfo.get(VNXFileConstants.ANON);

            // If both roHosts and rwHosts are null, accessHosts get "rw"
            // permission.
            // If either roHosts or rwHosts is non-null, accessHosts get
            // "ro" permission.
            if ((accessHosts != null) && (roHosts == null)) {
                // The non-null roHosts case is covered further below
                // Create a new unmanaged export
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithAccessHosts(
                        accessHosts, rwHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + accessHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }

            if (roHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRoHosts(
                        roHosts, accessHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + roHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }

            if (rwHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRwHosts(
                        rwHosts, exportPath, security, storagePort, anonUser, protocol);
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + rwHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.debug(debugInfo.toString());
            }

            if (rootHosts != null) {
                UnManagedFSExport unManagedfileExport = createUnManagedExportWithRootHosts(
                        rootHosts, exportPath, security, storagePort, anonUser, protocol);
                // TODO Separate create map and associate
                associateExportMapWithFS(vnxufs, unManagedfileExport);
                StringBuffer debugInfo = new StringBuffer();
                debugInfo.append("VNXFileExport: ");
                debugInfo.append(" Hosts : " + rootHosts.toString());
                debugInfo.append(" Mount point : " + exportPath);
                debugInfo.append(" Permission : " + unManagedfileExport.getPermissions());
                debugInfo.append(" Protocol : " + unManagedfileExport.getProtocol());
                debugInfo.append(" Security type : " + unManagedfileExport.getSecurityType());
                debugInfo.append(" Root user mapping : " + unManagedfileExport.getRootUserMapping());
                _logger.info(debugInfo.toString());
            }

        } catch (Exception ex) {
            _logger.warn("VNX file export retrieve processor failed for path {}, cause {}",
                    mountPath, ex);
        }
    }

    /**
     * get mount point from share and port
     * 
     * @param shareName - share name
     * @param storagePort - storage port
     * @return
     */
    private String getMountPount(String shareName, StoragePort storagePort) {

        String mountPoint = null;
        String portName = storagePort.getPortName();
        if (storagePort.getPortNetworkId() != null) {
            portName = storagePort.getPortNetworkId();
        }
        mountPoint = (portName != null) ? "\\\\" + portName + "\\" + shareName : null;
        return mountPoint;

    }

    /**
     * associate smbshares with unmanaged filesystem
     * 
     * @param vnxufs - unmanaged filesystem
     * @param exportPath - export path of the filesystem
     * @param fsExportInfo - export details in map
     * @param storagePort - storageport object
     */
    private void associateCifsExportWithFS(UnManagedFileSystem vnxufs,
            String exportPath, Map<String, String> fsExportInfo, StoragePort storagePort) {

        try {
            // Assign storage port to unmanaged FS
            if (storagePort != null && storagePort.getId() != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                vnxufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
                vnxufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }

            String shareName = fsExportInfo.get(VNXFileConstants.SHARE_NAME);
            String mountPoint = getMountPount(shareName, storagePort);
            UnManagedSMBFileShare unManagedSMBFileShare = new UnManagedSMBFileShare();
            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountPoint);
            unManagedSMBFileShare.setPath(exportPath);
            // setting to default permission type for VNX
            unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);
            unManagedSMBFileShare.setDescription(fsExportInfo.get(VNXFileConstants.SHARE_COMMENT));
            int maxUsers = Integer.MAX_VALUE;
            if (Long.parseLong(fsExportInfo.get(VNXFileConstants.SHARE_MAXUSR)) < Integer.MAX_VALUE) {
                maxUsers = Integer.parseInt(fsExportInfo.get(VNXFileConstants.SHARE_MAXUSR));
            }
            unManagedSMBFileShare.setMaxUsers(maxUsers);
            unManagedSMBFileShare.setPortGroup(storagePort.getPortGroup());

            unManagedSMBFileShare.setPermission(ShareACL.SupportedPermissions.change.toString());

            UnManagedSMBShareMap currUnManagedShareMap = vnxufs.getUnManagedSmbShareMap();
            if (currUnManagedShareMap == null) {
                currUnManagedShareMap = new UnManagedSMBShareMap();
                vnxufs.setUnManagedSmbShareMap(currUnManagedShareMap);
            }

            if (currUnManagedShareMap.get(shareName) == null) {
                currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _logger.info("associateCifsExportWithFS - no SMBs already exists for share {}",
                        shareName);
            } else {
                // Remove the existing and add the new share
                currUnManagedShareMap.remove(shareName);
                currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _logger.warn("associateSMBShareMapWithFS - Identical export already exists for mount path {} Overwrite",
                        shareName);
            }

        } catch (Exception ex) {
            _logger.warn("VNX file share retrieve processor failed for path {}, cause {}",
                    exportPath, ex);
        }
    }

    /**
     * create UnManagedFSExport with access hosts
     * 
     * @param accessHosts - list of access hosts
     * @param rwHosts - list of rw hosts
     * @param mountPath - mount path of the filesystem
     * @param security - security type
     * @param storagePort - storage port
     * @param anonUser - name of the user
     * @param protocol - protocol type
     * @return
     */
    private UnManagedFSExport createUnManagedExportWithAccessHosts(List<String> accessHosts,
            List<String> rwHosts, String mountPath, String security, StoragePort storagePort,
            String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath,
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(accessHosts);
        if (rwHosts == null) {
            unManagedfileExport.setPermissions(VNXFileConstants.RW);
        } else {
            unManagedfileExport.setPermissions(VNXFileConstants.RO);
        }
        return unManagedfileExport;
    }

    /**
     * create UnManagedFSExport with RO hosts
     * 
     * @param roHosts - list of hosts
     * @param accessHosts - list of access hosts
     * @param mountPath - filesystem mount path
     * @param security - security type
     * @param storagePort - port on which filesystem accessed
     * @param anonUser - name of user
     * @param protocol - protocol type
     * @return
     */
    private UnManagedFSExport createUnManagedExportWithRoHosts(List<String> roHosts,
            List<String> accessHosts, String mountPath, String security,
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath,
                security, storagePort, anonUser, protocol);
        List<String> readOnlyHosts = roHosts;
        if (accessHosts != null) {
            for (String accHost : accessHosts) {
                if (!(readOnlyHosts.contains(accHost))) {
                    readOnlyHosts.add(accHost);
                }
            }
        }
        unManagedfileExport.setClients(readOnlyHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.RO);
        return unManagedfileExport;
    }

    /**
     * create a UnManagedExport with Rw Hosts.
     * 
     * @param rwHosts
     * @param mountPath
     * @param security
     * @param storagePort
     * @param anonUser
     * @param protocol
     * @return
     */
    private UnManagedFSExport createUnManagedExportWithRwHosts(
            List<String> rwHosts, String mountPath, String security,
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath,
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(rwHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.RW);
        return unManagedfileExport;
    }

    /**
     * prepare UnManagedExport with RootHosts
     * 
     * @param rootHosts
     * @param mountPath
     * @param security
     * @param storagePort
     * @param anonUser
     * @param protocol
     * @return
     */
    private UnManagedFSExport createUnManagedExportWithRootHosts(
            List<String> rootHosts, String mountPath, String security,
            StoragePort storagePort, String anonUser, String protocol) {
        UnManagedFSExport unManagedfileExport = new UnManagedFSExport();
        setupUnManagedFSExportProperties(unManagedfileExport, mountPath,
                security, storagePort, anonUser, protocol);
        unManagedfileExport.setClients(rootHosts);
        unManagedfileExport.setPermissions(VNXFileConstants.ROOT);
        return unManagedfileExport;
    }

    /**
     * prepare the UnManaged Exportrule
     * 
     * @param id
     * @param exportPath
     * @param mountPoint
     * @param securityFlavor
     * @param anonUser
     * @param accessHosts
     * @param roHosts
     * @param rwHosts
     * @param rootHosts
     * @return
     */
    private UnManagedFileExportRule createUnManagedExportRule(URI id, String exportPath, String mountPoint,
            String securityFlavor, String anonUser, StringSet accessHosts,
            StringSet roHosts, StringSet rwHosts, StringSet rootHosts) {

        UnManagedFileExportRule umfsExpRule = new UnManagedFileExportRule();
        // Don't create the ID here ...
        umfsExpRule.setFileSystemId(id);
        umfsExpRule.setAnon(anonUser);
        umfsExpRule.setExportPath(exportPath);
        umfsExpRule.setMountPoint(mountPoint);
        umfsExpRule.setSecFlavor(securityFlavor);

        if (anonUser != null) {
            if (anonUser.equalsIgnoreCase(VNXFileConstants.ROOT_ANON_USER)) {
                umfsExpRule.setAnon(VNXFileConstants.ROOT);
            } else {
                umfsExpRule.setAnon(anonUser);
            }
        } else {
            umfsExpRule.setAnon(VNXFileConstants.NOBODY);
        }

        if (accessHosts != null && roHosts == null) {
            if (rwHosts == null) {
                umfsExpRule.setReadWriteHosts(accessHosts);
            } else {
                umfsExpRule.setReadOnlyHosts(accessHosts);
            }

        }
        if (roHosts != null) {
            StringSet readOnlyHosts = roHosts;

            if (accessHosts != null) {
                for (String accHost : accessHosts) {
                    if (!(readOnlyHosts.contains(accHost))) {
                        readOnlyHosts.add(accHost);
                    }
                }
            }
            umfsExpRule.setReadOnlyHosts(readOnlyHosts);
        }

        if (rwHosts != null) {
            umfsExpRule.setReadWriteHosts(rwHosts);
        }

        if (rootHosts != null) {
            umfsExpRule.setRootHosts(rootHosts);
        }

        return umfsExpRule;
    }

    /**
     * set the export properties such exportkey, path, mount path and point
     * 
     * @param unManagedfileExport
     * @param mountPath
     * @param security
     * @param storagePort
     * @param anonUser
     * @param protocol
     */
    private void setupUnManagedFSExportProperties(
            UnManagedFSExport unManagedfileExport, String mountPath,
            String security, StoragePort storagePort, String anonUser,
            String protocol) {
        unManagedfileExport.setMountPoint(storagePort.getPortNetworkId() + ":" + mountPath);
        unManagedfileExport.setPath(mountPath);
        unManagedfileExport.setMountPath(mountPath);
        unManagedfileExport.setProtocol(protocol);
        unManagedfileExport.setSecurityType(security);
        unManagedfileExport.setStoragePortName(storagePort.getPortName());
        unManagedfileExport.setStoragePort(storagePort.getId().toString());
        if (anonUser != null) {
            if (anonUser.equalsIgnoreCase(VNXFileConstants.ROOT_ANON_USER)) {
                unManagedfileExport.setRootUserMapping(VNXFileConstants.ROOT);
            } else {
                unManagedfileExport.setRootUserMapping(anonUser);
            }
        } else {
            unManagedfileExport.setRootUserMapping(VNXFileConstants.NOBODY);
        }
        _logger.debug("setupUnManagedFSExportProperties ExportKey : {} ", unManagedfileExport.getFileExportKey());
        _logger.debug("setupUnManagedFSExportProperties Path : {} ", unManagedfileExport.getPath());
        _logger.debug("setupUnManagedFSExportProperties Mount Path :{} ", unManagedfileExport.getMountPath());
        _logger.debug("setupUnManagedFSExportProperties Mount Point :{} ", unManagedfileExport.getMountPoint());
    }

    /**
     * associate export map with unmanaged filesystem
     * 
     * @param vnxufs - unmanaged filesystem object
     * @param unManagedfileExport - unmanaged filesystem export object
     */
    private void associateExportMapWithFS(UnManagedFileSystem vnxufs,
            UnManagedFSExport unManagedfileExport) {
        // TODO: create - separate
        UnManagedFSExportMap currUnManagedExportMap = vnxufs.getFsUnManagedExportMap();
        if (currUnManagedExportMap == null) {
            currUnManagedExportMap = new UnManagedFSExportMap();
            vnxufs.setFsUnManagedExportMap(currUnManagedExportMap);
        }
        String exportKey = unManagedfileExport.getFileExportKey();
        if (currUnManagedExportMap.get(exportKey) == null) {
            currUnManagedExportMap.put(exportKey, unManagedfileExport);
            _logger.debug("associateExportMapWithFS {} no export already exists for mount path {}",
                    exportKey, unManagedfileExport.getMountPath());
        } else {
            currUnManagedExportMap.put(exportKey, unManagedfileExport);
            _logger.warn("associateExportMapWithFS {} Identical export already exists for mount path {} Overwrite",
                    exportKey, unManagedfileExport.getMountPath());
        }

    }

    /**
     * create StorageFileSystem Info Object
     * 
     * @param unManagedFileSystem - unmanaged object
     * @param unManagedFileSystemNativeGuid - native guid of the filesystem
     * @param system - storage system object
     * @param pool - storagepool object
     * @param storagePort - storage port
     * @param fileSystem - VNXFileSystem object
     * @return UnManagedFileSystem -unmanaged file system object
     * @throws IOException
     * @throws VNXFileCollectionException
     */
    private UnManagedFileSystem createUnManagedFileSystem(
            UnManagedFileSystem unManagedFileSystem,
            String unManagedFileSystemNativeGuid, StorageSystem system, StoragePool pool,
            StoragePort storagePort, VNXFileSystem fileSystem) throws IOException, VNXFileCollectionException {
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setStoragePoolUri(pool.getId());
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        if (fileSystem.getType().equals(UnManagedDiscoveredObject.SupportedProvisioningType.THICK.name())) {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(),
                    FALSE);
        } else {
            unManagedFileSystemCharacteristics.put(
                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                            .toString(),
                    TRUE);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(),
                FALSE);

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            // We should check matched vpool based on storagepool of type for given fs.
            // In vipr, storagepool of thin is taken as THICK
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());
            _logger.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpools.
                unManagedFileSystem.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedFileSystem.getSupportedVpoolUris().replace(matchedVPools);
                _logger.info("Replaced Pools :"
                        + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
            }

        }

        if (null != storagePort) {
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(),
                TRUE);
        // Set attributes of FileSystem
        StringSet fsPath = new StringSet();
        fsPath.add("/" + fileSystem.getFsName());

        StringSet fsMountPath = new StringSet();
        fsMountPath.add("/" + fileSystem.getFsName());

        StringSet fsName = new StringSet();
        fsName.add(fileSystem.getFsName());

        StringSet fsId = new StringSet();
        fsId.add(fileSystem.getFsId() + "");

        unManagedFileSystem.setLabel(fileSystem.getFsName());

        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(), fsId);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PATH.toString(), fsPath);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);

        StringSet allocatedCapacity = new StringSet();
        String usedCapacity = "0";
        if (fileSystem.getUsedCapacity() != null) {
            usedCapacity = fileSystem.getUsedCapacity();
        }
        allocatedCapacity.add(usedCapacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(),
                allocatedCapacity);

        StringSet provisionedCapacity = new StringSet();
        String capacity = "0";
        if (fileSystem.getTotalCapacity() != null) {
            capacity = fileSystem.getTotalCapacity();
        }
        provisionedCapacity.add(capacity);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(),
                provisionedCapacity);

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .setFileSystemCharacterstics(unManagedFileSystemCharacteristics);

        return unManagedFileSystem;
    }

    /**
     * set vnxfile request params
     * 
     * @param system - vnx storage system
     * @return
     */
    private Map<String, Object> getRequestParamsMap(final StorageSystem system) {

        Map<String, Object> reqAttributeMap = new ConcurrentHashMap<String, Object>();
        reqAttributeMap.put(VNXFileConstants.DEVICETYPE, system.getSystemType());
        reqAttributeMap.put(VNXFileConstants.DBCLIENT, _dbClient);
        reqAttributeMap.put(VNXFileConstants.USERNAME, system.getUsername());
        reqAttributeMap.put(VNXFileConstants.USER_PASS_WORD, system.getPassword());
        reqAttributeMap.put(VNXFileConstants.PORTNUMBER, system.getPortNumber());

        AccessProfile profile = new AccessProfile();
        profile.setIpAddress(system.getIpAddress());

        reqAttributeMap.put(VNXFileConstants.URI, getServerUri(profile));
        reqAttributeMap.put(VNXFileConstants.AUTHURI, getLoginUri(profile));
        return reqAttributeMap;
    }

    /**
     * check Storage fileSystem exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws java.io.IOException
     */
    protected boolean checkStorageFileSystemExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(nativeGuid), result);
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI fileSystemtURI = iter.next();
            FileShare fileShare = _dbClient.queryObject(FileShare.class, fileSystemtURI);
            if (fileShare != null && !fileShare.getInactive()) {
                return true;
            }
        }
        return false;
    }

    /**
     * check PreExisting Storage filesystem exists in DB
     * 
     * @param nativeGuid - native guid of the filesystem
     * @return unManageFileSystem - unmanaged object
     * @throws IOException
     */
    protected UnManagedFileSystem checkUnManagedFileSystemExistsInDB(
            String nativeGuid) throws IOException {
        UnManagedFileSystem filesystemInfo = null;
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemInfoNativeGUIdConstraint(nativeGuid), result);
        List<URI> filesystemUris = new ArrayList<URI>();
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unFileSystemtURI = iter.next();
            filesystemUris.add(unFileSystemtURI);
        }
        for (URI fileSystemURI : filesystemUris) {
            filesystemInfo = _dbClient.queryObject(UnManagedFileSystem.class,
                    fileSystemURI);
            if (filesystemInfo != null && !filesystemInfo.getInactive()) {
                return filesystemInfo;
            }
        }

        return null;

    }

    /**
     * get storage ports
     * 
     * @param storageSystem -vnxfile system object
     * @return
     * @throws IOException
     */
    private StoragePort getStoragePortPool(StorageSystem storageSystem)
            throws IOException {
        StoragePort storagePort = null;
        // Check if storage port was already discovered
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while (storagePortIter.hasNext()) {
            URI storagePortURI = storagePortIter.next();
            storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if (storagePort != null && !storagePort.getInactive()) {
                _logger.debug("found a port for storage system  {} {}",
                        storageSystem.getSerialNumber(), storagePort);
                return storagePort;
            }
        }
        return null;
    }

    /**
     * get the all data movers
     * 
     * @param storageSystem - storage system object
     * @return
     * @throws IOException
     */
    private Map<String, StorageHADomain> getAllDataMovers(StorageSystem storageSystem)
            throws IOException {

        Map<String, StorageHADomain> allDataMovers = new ConcurrentHashMap<>();

        List<URI> storageAdapterURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStorageHADomainConstraint(storageSystem
                                .getId()));
        List<StorageHADomain> dataMovers = _dbClient.queryObject(
                StorageHADomain.class, storageAdapterURIs);

        for (StorageHADomain dm : dataMovers) {
            if (!dm.getInactive() && !dm.getVirtual()) {
                _logger.info("found a Physical StorageHADomain for storage system  {} {}",
                        storageSystem.getSerialNumber(), dm.getAdapterName());
                allDataMovers.put(dm.getAdapterName(), dm);
            }
        }
        return allDataMovers;
    }

    /**
     * get the all VDMs
     * 
     * @param storageSystem - vnx storagesystem object
     * @return
     * @throws IOException
     */
    private Map<String, StorageHADomain> getAllVDMs(StorageSystem storageSystem)
            throws IOException {

        Map<String, StorageHADomain> allVDMs = new ConcurrentHashMap<>();

        List<URI> storageAdapterURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceStorageHADomainConstraint(storageSystem
                                .getId()));
        List<StorageHADomain> dataMovers = _dbClient.queryObject(
                StorageHADomain.class, storageAdapterURIs);

        for (StorageHADomain dm : dataMovers) {
            if (!dm.getInactive() && dm.getVirtual()) {
                _logger.info("found a Virtual StorageHADomain for storage system  {} {}",
                        storageSystem.getSerialNumber(), dm.getAdapterName());
                allVDMs.put(dm.getAdapterName(), dm);
            }
        }
        return allVDMs;
    }

    /**
     * get the Name and fsid map
     * 
     * @param storageSystem
     * @return
     */
    private Map<String, String> getFsNameFsNativeIdMap(StorageSystem storageSystem) {
        HashMap<String, String> nameNativeIdMap = new HashMap<>();

        List<URI> umFsURIs = _dbClient
                .queryByConstraint(ContainmentConstraint.Factory
                        .getStorageDeviceUnManagedFileSystemConstraint(storageSystem
                                .getId()));

        List<UnManagedFileSystem> umFSs = _dbClient.queryObject(
                UnManagedFileSystem.class, umFsURIs);

        for (UnManagedFileSystem umFS : umFSs) {
            String fsName = extractValueFromStringSet(
                    UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(),
                    umFS.getFileSystemInformation());

            String fsNativeId = extractValueFromStringSet(
                    UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(),
                    umFS.getFileSystemInformation());

            nameNativeIdMap.put(fsName, fsNativeId);
            _logger.debug("getFsNameFsNativeIdMap {} : {}", fsName, fsNativeId);
        }

        return nameNativeIdMap;
    }

    /**
     * get the value for key from Map of volume information
     * 
     * @param key
     * @param volumeInformation
     * @return
     */
    public static String extractValueFromStringSet(String key, StringSetMap volumeInformation) {
        try {
            StringSet availableValueSet = volumeInformation.get(key);
            if (null != availableValueSet) {
                for (String value : availableValueSet) {
                    return value;
                }
            }
        } catch (Exception e) {
            _logger.error(e.getMessage());
        }
        return null;
    }

}
