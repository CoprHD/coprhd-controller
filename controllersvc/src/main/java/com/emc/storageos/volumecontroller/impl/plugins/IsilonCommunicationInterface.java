/*
 * Copyright 2015 EMC Corporation
 * All Rights Reserved
 */
/**
 *  Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 *
 * This software contains the intellectual property of EMC Corporation
 * or is licensed to EMC Corporation from third parties.  Use of this
 * software and the intellectual property contained therein is expressly
 * limited to the terms and conditions of the License Agreement under which
 * it is provided by or on behalf of EMC.
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.*;
import com.emc.storageos.isilon.restapi.*;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedVolume.SupportedVolumeInformation;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonClusterConfig;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfo;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfoV2;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.isilon.restapi.IsilonStoragePool;
import com.emc.storageos.isilon.restapi.IsilonStoragePort;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.metering.isilon.IsilonCollectionException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileDBInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.isilon.IsilonStatsRecorder;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Class for Isilon discovery and collecting stats from Isilon storage device
 */
public class IsilonCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private Logger _log = LoggerFactory.getLogger(IsilonCommunicationInterface.class);
    private static final String POOL_TYPE = "IsilonNodePool";
    private static final int BYTESCONVERTER= 1024;
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedExportRule";
    private static final String UNMANAGED_SHARE_ACL = "UnManagedCifsShareACL";
    private static final String IFS_ROOT = "/ifs";
    private static final String SOS_DIR = "sos";
    private static final String QUOTA = "quota";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final String RO = "ro";
    private static final String RW = "rw";
    private static final String ROOT = "root";
    private static final String NFS = "NFS";
    private static final String UNIXSECURITY = "unix";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String SYSSECURITY = "sys";

    private IsilonApiFactory _factory;

    private List<String> _discPathsForUnManaged;

    /**
     * Get Unmanaged File System Container paths
     *
     * @return List object
     */
    public List<String> getDiscPathsForUnManaged() {
		return _discPathsForUnManaged;
	}

     /**
     * Set Unmanaged File System Container paths
     *
     * @param ;discPathsForUnManaged
     */
    public void setDiscPathsForUnManaged(List<String> discPathsForUnManaged) {
		this._discPathsForUnManaged = discPathsForUnManaged;
	}

    /**
     * Set Isilon API factory
     *
     * @param ;factory
     */
    public void setIsilonApiFactory(IsilonApiFactory factory) {
        _factory = factory;
    }


    /**
     * Get isilon device represented by the StorageDevice
     *
     * @param accessProfile
     *            StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     * @throws URISyntaxException
     */
    private IsilonApi getIsilonDevice(AccessProfile accessProfile) throws IsilonException, URISyntaxException {
    	URI deviceURI = new URI("https", null, accessProfile.getIpAddress(), accessProfile.getPortNumber(), "/", null, null);
        // if no username, assume its the isilon simulator device
        if (accessProfile.getUserName() != null && !accessProfile.getUserName().isEmpty()) {
            return _factory
                    .getRESTClient(deviceURI, accessProfile.getUserName(), accessProfile.getPassword());
        } else {
            return _factory.getRESTClient(deviceURI);
        }
    }


    /**
     * Get isilon device represented by the StorageDevice
     *
     * @param isilonCluster  StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     * @throws URISyntaxException
     */
    private IsilonApi getIsilonDevice(StorageSystem isilonCluster) throws IsilonException, URISyntaxException {
    	URI deviceURI = new URI("https", null, isilonCluster.getIpAddress(), isilonCluster.getPortNumber(), "/", null, null);

        return _factory
                .getRESTClient(deviceURI, isilonCluster.getUsername(), isilonCluster.getPassword());
    }


    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        URI storageSystemId = null;
        StorageSystem isilonCluster = null;
        long statsCount = 0;
        try {
            _log.info("Metering for {} using ip {}", accessProfile.getSystemId(),
                    accessProfile.getIpAddress());
            IsilonApi api = getIsilonDevice(accessProfile);
            long latestSampleTime = accessProfile.getLastSampleTime();
            storageSystemId = accessProfile.getSystemId();
            isilonCluster = _dbClient.queryObject(StorageSystem.class, storageSystemId);
            String serialNumber = isilonCluster.getSerialNumber();
            String deviceType = isilonCluster.getSystemType();
            initializeKeyMap(accessProfile);
            List<Stat> stats = new ArrayList<Stat>();

            ZeroRecordGenerator zeroRecordGenerator = new FileZeroRecordGenerator();
            CassandraInsertion statsColumnInjector = new FileDBInsertion();
            // get usage stats from quotas
            IsilonStatsRecorder recorder = new IsilonStatsRecorder(zeroRecordGenerator, statsColumnInjector);
            _keyMap.put(Constants._TimeCollected, System.currentTimeMillis());

            // get first page of quota data, process and insert to database
            IsilonApi.IsilonList<IsilonSmartQuota> quotas = api.listQuotas(null);
            for (IsilonSmartQuota quota : quotas.getList()) {
                String fsNativeId = quota.getPath();
                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(deviceType, serialNumber, fsNativeId);
                Stat stat = recorder.addUsageStat(quota, _keyMap, fsNativeGuid, api);
                if (null != stat) {
                    stats.add(stat);
                    // Persists the file system, only if change in used capacity.
                    FileShare fileSystem = _dbClient.queryObject(FileShare.class, stat.getResourceId());
                    if (fileSystem != null) {
                        if (!fileSystem.getInactive() && fileSystem.getUsedCapacity() != stat.getAllocatedCapacity()) {
                            fileSystem.setUsedCapacity(stat.getAllocatedCapacity());
                            _dbClient.persistObject(fileSystem);
                        }
                    }
                }
            }
            persistStatsInDB(stats);
            statsCount = statsCount + quotas.size();
            _log.info("Processed {} file system stats for device {} ", quotas.size(), storageSystemId);

            // get all other pages of quota data, process and insert to database page by page
            while (quotas.getToken() != null && !quotas.getToken().isEmpty()) {
                quotas = api.listQuotas(quotas.getToken());
                for (IsilonSmartQuota quota : quotas.getList()) {
                    String fsNativeId = quota.getPath();
                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(deviceType, serialNumber, fsNativeId);
                    Stat stat = recorder.addUsageStat(quota, _keyMap, fsNativeGuid, api);
                    if (null != stat) {
                        stats.add(stat);
                        // Persists the file system, only if change in used capacity.
                        FileShare fileSystem = _dbClient.queryObject(FileShare.class, stat.getResourceId());
                        if (fileSystem != null) {
                            if (!fileSystem.getInactive() && fileSystem.getUsedCapacity() != stat.getAllocatedCapacity()) {
                               fileSystem.setUsedCapacity(stat.getAllocatedCapacity());
                               _dbClient.persistObject(fileSystem);
                            }
                        }
                    }
                }
                statsCount = statsCount + quotas.size();
                _log.info("Processed {} file system stats for device {} ", quotas.size(), storageSystemId);
            }
            zeroRecordGenerator.identifyRecordstobeZeroed(_keyMap, stats, FileShare.class);
            persistStatsInDB(stats);
            latestSampleTime = System.currentTimeMillis();
            accessProfile.setLastSampleTime(latestSampleTime);
            _log.info("Done metering device {}, processed {} file system stats ", storageSystemId, statsCount);
        } catch (Exception e) {
            if (isilonCluster != null) {
                cleanupDiscovery(isilonCluster);
            }
            _log.error("CollectStatisticsInformation failed. Storage system: " + storageSystemId, e);
            throw (new IsilonCollectionException(e.getMessage()));
        }
    }

    /**
     * Dump records on disk & persist the records in db.
     */
    private void persistStatsInDB(List<Stat> stats) throws BaseCollectionException {
        if (!stats.isEmpty()) {
            _keyMap.put(Constants._Stats, stats);
            dumpStatRecords();
            //Persist in db after processing the paged data.
            injectStats();
            // clear collection as we have already persisted in db.
            stats.clear();
        }
    }

    /**
     * populate keyMap with required attributes.
     */
    private void initializeKeyMap(AccessProfile accessProfile) {
        _keyMap.put(Constants.dbClient, _dbClient);
        _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
        _keyMap.put(Constants.PROPS, accessProfile.getProps());
        _keyMap.put(Constants._serialID, accessProfile.getserialID());
        _keyMap.put(Constants._nativeGUIDs, Sets.newHashSet());
    }


    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        _log.info("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                accessProfile.getIpAddress() +":" + accessProfile.getPortNumber(),
                accessProfile.getnamespace());

        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                        .toString()))) {
            discoverUmanagedFileSystems(accessProfile);
        } else {
            discoverAll(accessProfile);
        }
    }

    public void discoverAll(AccessProfile accessProfile) throws BaseCollectionException {
        URI storageSystemId = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);

            // try to connect to the Isilon cluster first to check if cluster is available
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            isilonApi.getClusterInfo();

            discoverCluster(storageSystem);
            _dbClient.persistObject(storageSystem);
            if (!storageSystem.getReachableStatus())  {
                throw new IsilonCollectionException("Failed to connect to " + storageSystem.getIpAddress());
            }
            _completer.statusPending(_dbClient, "Completed cluster discovery");
            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            // discover pools
            Map<String, List<StoragePool>> pools = discoverPools(storageSystem, poolsToMatchWithVpool);
            _log.info("No of newly discovered pools {}", pools.get(NEW).size());
            _log.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if(!pools.get(NEW).isEmpty()){
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if(!pools.get(EXISTING).isEmpty()){
                allPools.addAll(pools.get(EXISTING));
                _dbClient.persistObject(pools.get(EXISTING));
            }
            
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(allPools,
                    _dbClient, storageSystemId);
            poolsToMatchWithVpool.addAll(notVisiblePools);
            _completer.statusPending(_dbClient, "Completed pool discovery");

            // discover ports
            List<StoragePort> allPorts = new ArrayList<StoragePort>();
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem);
            _log.info("No of newly discovered ports {}", ports.get(NEW).size());
            _log.info("No of existing discovered ports {}", ports.get(EXISTING).size());
            if(null != ports && !ports.get(NEW).isEmpty()) {
                allPorts.addAll(ports.get(NEW));
                _dbClient.createObject(ports.get(NEW));
            }

            if(null != ports && !ports.get(EXISTING).isEmpty()) {
                allPorts.addAll(ports.get(EXISTING));
                _dbClient.persistObject(ports.get(EXISTING));
            }
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                    _dbClient, storageSystemId);    
            List<StoragePort> allExistPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            allExistPorts.addAll(notVisiblePorts);
            _completer.statusPending(_dbClient,"Completed port discovery");

            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW), 
                    allExistPorts, _dbClient, _coordinator, poolsToMatchWithVpool);

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Isilon: %s",
                    storageSystemId.toString());
        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for Isilon %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _log.error(detailedStatusMessage, e);
            throw new IsilonCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    private void discoverCluster(StorageSystem storageSystem) throws IsilonCollectionException {

        URI storageSystemId = storageSystem.getId();

        try {
            _log.info("discoverCluster information for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            IsilonClusterConfig clusterConfig = isilonApi.getClusterConfig();

            storageSystem.setSerialNumber(clusterConfig.getGuid());
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(DiscoveredDataObject.Type.isilon.toString(),
                    clusterConfig.getGuid());
            storageSystem.setNativeGuid(nativeGuid);
            String clusterReleaseVersion = clusterConfig.getOnefs_version_info().getReleaseVersionNumber();
            storageSystem.setFirmwareVersion(clusterReleaseVersion);

            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(storageSystem.getSystemType()));
            _log.info("Verifying version details : Minimum Supported Version {} - Discovered Cluster Version {}", minimumSupportedVersion, clusterReleaseVersion);
            if(VersionChecker.verifyVersionDetails(minimumSupportedVersion, clusterReleaseVersion) < 0)
            {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                IsilonCollectionException ice = new IsilonCollectionException(String.format(" ** This version of Isilon firmware is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw ice;
            }
            storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            storageSystem.setReachableStatus(true);

            _log.info("discoverCluster information for storage system {} - complete", storageSystemId);
        }  catch (Exception e) {
            storageSystem.setReachableStatus(false);
            String errMsg = String.format("discoverCluster failed. %s", e.getMessage());
            _log.error(errMsg, e);
            IsilonCollectionException ice = new IsilonCollectionException(errMsg);
            throw ice;
        }
    }

    private Map<String, List<StoragePool>> discoverPools(StorageSystem storageSystem, List<StoragePool> poolsToMatchWithVpool)
            throws IsilonCollectionException {

        // Discover storage pools
        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        URI storageSystemId = storageSystem.getId();

        try {
            _log.info("discoverPools for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            StoragePool storagePool;

            List<IsilonStoragePool> isilonStoragePools = isilonApi.getStoragePools();
            for (IsilonStoragePool isilonPool : isilonStoragePools)  {
                // Check if this storage pool was already discovered
                storagePool = null;
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, isilonPool.getNativeId(),
                        NativeGUIDGenerator.POOL);
                @SuppressWarnings("deprecation")
                List<URI> poolURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getStoragePoolByNativeGuidConstraint(poolNativeGuid));

                for (URI poolUri : poolURIs ) {
                    StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
                    if (!pool.getInactive()&&pool.getStorageDevice().equals(storageSystemId)) {
                        storagePool = pool;
                        break;
                    }
                }

                if (storagePool == null)
                {
                    // New storage pool
                    storagePool = new StoragePool();
                    storagePool.setId(URIUtil.createId(StoragePool.class));
                    storagePool.setNativeGuid(poolNativeGuid);
                    storagePool.setLabel(poolNativeGuid);
                    storagePool.setPoolClassName(POOL_TYPE);
                    storagePool.setPoolServiceType(PoolServiceType.file.toString());
                    storagePool.setStorageDevice(storageSystemId);

                    StringSet protocols = new StringSet();
                    protocols.add("NFS");
                    protocols.add("CIFS");
                    storagePool.setProtocols(protocols);
                    storagePool.setPoolName(isilonPool.getNativeId());
                    storagePool.setNativeId(isilonPool.getNativeId());
                    storagePool.setLabel(poolNativeGuid);
                    storagePool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString());
                    storagePool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.toString());
                    storagePool.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _log.info("Creating new storage pool using NativeGuid : {}", poolNativeGuid);
                    newPools.add(storagePool);
                } else {
                    existingPools.add(storagePool);
                }

                // scale capacity size
                storagePool.setFreeCapacity(isilonPool.getAvailable()/BYTESCONVERTER);
                storagePool.setTotalCapacity(isilonPool.getTotal()/BYTESCONVERTER);
                storagePool.setSubscribedCapacity(isilonPool.getUsed()/BYTESCONVERTER);
                if(ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getCompatibilityStatus(), DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())){
                    poolsToMatchWithVpool.add(storagePool);
                }
                storagePool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                storagePool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
            _log.info("discoverPools for storage system {} - complete", storageSystemId);

            storagePools.put(NEW, newPools);
            storagePools.put(EXISTING, existingPools);
            return storagePools;

        } catch (IsilonException ie) {
            _log.error("discoverPools failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverPools failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        }
        catch (Exception e) {
            _log.error("discoverPools failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverPools failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }


    private HashMap<String, List<StoragePort>> discoverPorts(StorageSystem storageSystem) throws IsilonCollectionException {

        URI storageSystemId = storageSystem.getId();
        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();
        // Discover storage ports
        try {
            _log.info("discoverPorts for storage system {} - start", storageSystemId);
            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            List<IsilonStoragePort> isilonStoragePorts = new ArrayList<IsilonStoragePort>();

            try {
                    _log.info("Trying to get latest smart connect version");
                    IsilonSmartConnectInfoV2 connInfo = isilonApi.getSmartConnectInfoV2();
                    if(connInfo == null || (connInfo != null && connInfo.getSmartZones() == null)) {
                        throw new Exception("Failed new Interface, try old Interface");
                    }
                    if(connInfo != null){
                        isilonStoragePorts = connInfo.getPorts();
                    }
            } catch (Exception e) {
                    _log.info("Latest version failed so Trying to get old smart connect version");
                    IsilonSmartConnectInfo connInfo = isilonApi.getSmartConnectInfo();
                    if(connInfo != null){
                       isilonStoragePorts = connInfo.getPorts();
                    }
            }

            if( isilonStoragePorts == null || isilonStoragePorts.isEmpty()) {
                //No ports defined throw an exception and fail the discovery
                IsilonCollectionException ice = new IsilonCollectionException("discoverPorts failed. No Smartzones defined");
                throw ice;
            }

            for (IsilonStoragePort isilonPort : isilonStoragePorts)  {

                StoragePort storagePort = null;

                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, isilonPort.getIpAddress(),
                        NativeGUIDGenerator.PORT);
                // Check if storage port was already discovered
                @SuppressWarnings("deprecation")
                List<URI> portURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getStoragePortByNativeGuidConstraint(portNativeGuid));
                for (URI portUri : portURIs ) {
                    StoragePort port = _dbClient.queryObject(StoragePort.class, portUri);
                    if (port.getStorageDevice().equals(storageSystemId) && !port.getInactive()) {
                        storagePort = port;
                        break;
                    }
                }
                if (storagePort == null)
                {
                    // Create Isilon storage port for Isilon cluster IP address (smart connect ip)
                    storagePort = new StoragePort();
                    storagePort.setId(URIUtil.createId(StoragePort.class));
                    storagePort.setTransportType("IP");
                    storagePort.setNativeGuid(portNativeGuid);
                    storagePort.setLabel(portNativeGuid);
                    storagePort.setStorageDevice(storageSystemId);
                    storagePort.setPortNetworkId(isilonPort.getIpAddress().toLowerCase());
                    storagePort.setPortName(isilonPort.getPortName());
                    storagePort.setLabel(isilonPort.getPortName());
                    storagePort.setPortSpeed(isilonPort.getPortSpeed());
                    storagePort.setPortGroup(isilonPort.getPortName());
                    storagePort.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _log.info("Creating new storage port using NativeGuid : {}", portNativeGuid);
                    newStoragePorts.add(storagePort);
                } else {
                    existingStoragePorts.add(storagePort);
                }
                storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
            _log.info("discoverPorts for storage system {} - complete", storageSystemId);

            storagePorts.put(NEW, newStoragePorts);
            storagePorts.put(EXISTING, existingStoragePorts);
            return storagePorts;
        } catch (Exception e) {
            _log.error("discoverPorts failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverPorts failed. Storage system: " + storageSystemId);
            throw ice;
        }
    }

    private void discoverUmanagedFileSystems(AccessProfile profile)  throws BaseCollectionException {

        _log.debug("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                profile.getIpAddress() + profile.getPortNumber(),
                profile.getnamespace());

        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if(null == storageSystem){
            return;
        }

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();

        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();

        String detailedStatusMessage = "Discovery of Isilon Unmanaged FileSystem started";
        long unmanagedFsCount= 0;
        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                            .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                            storagePoolURIs);

            ArrayList<StoragePool> pools = new ArrayList();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                if(storagePool!=null && !storagePool.getInactive()){
                	pools.add(storagePool);
                }
            }

            StoragePool storagePool = null;
            if(pools != null && !pools.isEmpty()) {
               storagePool = pools.get(0);
            }

            StoragePort storagePort = getStoragePortPool(storageSystem);

            String resumeToken = null;

            int totalIsilonFSDiscovered = 0;

            //Get All FileShare
            HashMap<String, HashSet<String>> allSMBShares = discoverAllSMBShares(storageSystem);
            List<UnManagedCifsShareACL> unManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            List<UnManagedCifsShareACL> oldunManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();

            HashMap<String, HashSet<Integer>> expMap = discoverAllExports(storageSystem);
            
			UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
					_dbClient);
			List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();
			
            List<FileShare> discoveredFS = new ArrayList<FileShare>();
            do {
                IsilonApi.IsilonList<FileShare> discoveredIsilonFS = discoverAllFileSystem(storageSystem, resumeToken);
                resumeToken = discoveredIsilonFS.getToken();

                discoveredFS = discoveredIsilonFS.getList();

                totalIsilonFSDiscovered += discoveredFS.size();

                unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
                existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
                int newFileSystemsCount = 0;
                int existingFileSystemsCount = 0;
                HashMap<String, HashMap<String, HashSet<Integer>>> exportMapTree = getExportsWithSubDirForFS(discoveredFS, expMap);

                for(FileShare fs: discoveredFS){
                    if(!checkStorageFileSystemExistsInDB(fs.getNativeGuid())){
                    	//Create UnManaged FS
                    	 String fsUnManagedFsNativeGuid =
                                 NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                         storageSystem.getSerialNumber(), fs.getNativeId());

                        UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                        boolean alreadyExist = unManagedFs == null ? false : true;
                        unManagedFs = createUnManagedFileSystem(unManagedFs,
                                fsUnManagedFsNativeGuid, storageSystem, storagePool, storagePort, fs);

                        //get umcifs & ACLs for given filesystem
                        UnManagedCifsShareACL existingACL = null;
                        List<UnManagedCifsShareACL> tempunManagedCifsShareACL = new ArrayList<UnManagedCifsShareACL>();
                        int noOfShares = 0;
                        HashSet<String> smbShares = allSMBShares.get(fs.getPath());
                        if(smbShares != null && !smbShares.isEmpty()) {
                            //get UnManaged ACL and also set the shares in fs object
                            getUnmanagedCifsShareACL(unManagedFs, smbShares,
                                    tempunManagedCifsShareACL, storagePort, fs.getName(), isilonApi);
                            noOfShares += 1;
                            if (!tempunManagedCifsShareACL.isEmpty()) {
                                unManagedFs.setHasShares(true);
                                for (UnManagedCifsShareACL unManagedCifsShareACL : tempunManagedCifsShareACL) {
                                    _log.info("Unmanaged File share acls : {}", unManagedCifsShareACL);
                                    String fsShareNativeId = unManagedCifsShareACL.getFileSystemShareACLIndex();
                                    _log.info("UMFS Share ACL index {}", fsShareNativeId);
                                    String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                            .generateNativeGuidForPreExistingFileShare(storageSystem, fsShareNativeId);
                                    _log.info("Native GUID {}", fsUnManagedFileShareNativeGuid);
                                    //set native guid, so each entry unique
                                    unManagedCifsShareACL.setNativeGuid(fsUnManagedFileShareNativeGuid);
                                    // Check whether the CIFS share ACL was present in ViPR DB.
                                    existingACL = checkUnManagedFsCifsACLExistsInDB(_dbClient,
                                            unManagedCifsShareACL.getNativeGuid());
                                    if (existingACL == null) {
                                        unManagedCifsShareACLList.add(unManagedCifsShareACL);
                                    } else {
                                        unManagedCifsShareACLList.add(unManagedCifsShareACL);
                                        // delete the existing acl
                                        existingACL.setInactive(true);
                                        oldunManagedCifsShareACLList.add(existingACL);
                                    }
                                }
                                _log.info("File System {} has shares and their size is {}", unManagedFs.getId(), noOfShares);
                                _log.info("File System {} has ACL and their size is {}", unManagedFs.getId(), tempunManagedCifsShareACL.size());
                            }
                        }

                        //Get Export info
                        _log.info("Getting export for {}", fs.getPath());
                        HashMap<String, HashSet<Integer>> expIdMap = exportMapTree.get(fs.getPath());

                        if(expIdMap == null) {
                            expIdMap = new HashMap<>();
                        }
                        
                        List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();
                        if(!expIdMap.keySet().isEmpty()) {
                            boolean validExportsFound  = getUnManagedFSExportMap(unManagedFs, expIdMap, storagePort, fs.getPath(), isilonApi);
                            if(!validExportsFound) {
                                //Invalid exports so ignore the FS
                                String invalidExports = "";
                                for(String path:expIdMap.keySet()){
                                    invalidExports+=expIdMap.get(path);
                                }
                                _log.info("FS {} is ignored because it has conflicting exports {}", fs.getPath(), invalidExports);
                                unManagedFs.setInactive(true);
                                continue;
                            }
                        	List<UnManagedFileExportRule> validExportRules  = getUnManagedFSExportRules(unManagedFs, expIdMap, storagePort, fs.getPath(), isilonApi);
        					_log.info("Number of exports discovered for file system {} is {}", unManagedFs.getId(), validExportRules.size());
                            UnManagedFileExportRule existingRule = null;
        					for (UnManagedFileExportRule dbExportRule : validExportRules) {
        						_log.info("Un Managed File Export Rule : {}", dbExportRule);
        						String fsExportRulenativeId = dbExportRule.getFsExportIndex();
        						_log.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
        						String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
        								.generateNativeGuidForPreExistingFileExportRule(
        										storageSystem, fsExportRulenativeId);
        						_log.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);
        						dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
        						dbExportRule.setFileSystemId(unManagedFs.getId());
        						dbExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
                                existingRule = checkUnManagedFsExportRuleExistsInDB(_dbClient, dbExportRule.getNativeGuid());
                                if(null == existingRule){
                                    unManagedExportRules.add(dbExportRule);
                                } else {
                                    existingRule.setInactive(true);
                                    _dbClient.persistObject(existingRule);
                                    unManagedExportRules.add(dbExportRule);
                                }
        					}
        					
        					// Validate Rules Compatible with ViPR - Same rules should
        					// apply as per API SVC Validations.
                            if(!unManagedExportRules.isEmpty()) {
                                _log.info("Validating rules success for export {}", fs.getName());
                                newUnManagedExportRules.addAll(unManagedExportRules);
                                unManagedFs.setHasExports(true);
                                _log.info("File System {} has Exports and their size is {}", unManagedFs.getId(), newUnManagedExportRules.size());
                            }
                        }
                        if(expIdMap.keySet().isEmpty() && noOfShares == 0){
                            //NO exports found
                            _log.info("FS {} is ignored because it doesnt have exports and shares", fs.getPath());
                            unManagedFs.setInactive(true);
                        }

                        if (alreadyExist) {
                            existingUnManagedFileSystems.add(unManagedFs);
                            existingFileSystemsCount++;
                        } else {
                            unManagedFileSystems.add(unManagedFs);
                            newFileSystemsCount++;
                        }
    					if (!newUnManagedExportRules.isEmpty()) {
    						_log.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
    						_partitionManager.updateInBatches(
    								newUnManagedExportRules,
    								Constants.DEFAULT_PARTITION_SIZE, _dbClient,
    								UNMANAGED_EXPORT_RULE);
    						newUnManagedExportRules.clear();
    					}
                        //save ACLs in db
                        if(!unManagedCifsShareACLList.isEmpty() && unManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                            _log.info("Saving Number of UnManagedCifsShareACL(s) {}", unManagedCifsShareACLList.size());
                            _dbClient.createObject(unManagedCifsShareACLList);
                            unManagedCifsShareACLList.clear();
                        }
                        //save old acls
                        if (!oldunManagedCifsShareACLList.isEmpty() && oldunManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                            _log.info("Saving Number of UnManagedFileExportRule(s) {}", oldunManagedCifsShareACLList.size());
                            _dbClient.persistObject(oldunManagedCifsShareACLList);
                            oldunManagedCifsShareACLList.clear();
                        }
                        allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());
                        /**
                         * Persist 200 objects and clear them to avoid memory issue
                         */
                        validateListSizeLimitAndPersist(unManagedFileSystems, existingUnManagedFileSystems,
                                        Constants.DEFAULT_PARTITION_SIZE * 2);

                    }
                }
                _log.info("New unmanaged Isilon file systems count: {}", newFileSystemsCount);
                _log.info("Update unmanaged Isilon file systems count: {}", existingFileSystemsCount);
                if(!unManagedFileSystems.isEmpty()) {
                     _dbClient.createObject(unManagedFileSystems);
                }
                if(!existingUnManagedFileSystems.isEmpty()) {
                     _dbClient.persistObject(existingUnManagedFileSystems);
                }

            } while(resumeToken != null);

            //save ACLs in db
            if(!unManagedCifsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedCifsShareACL(s) {}", unManagedCifsShareACLList.size());
                _dbClient.createObject(unManagedCifsShareACLList);
                unManagedCifsShareACLList.clear();
            }
            //save old acls
            if (!oldunManagedCifsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedFileExportRule(s) {}", oldunManagedCifsShareACLList.size());
                _dbClient.persistObject(oldunManagedCifsShareACLList);
                oldunManagedCifsShareACLList.clear();
            }

            _log.info("Discovered {} Isilon file systems.", totalIsilonFSDiscovered);
            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Isilon: %s; new unmanaged file systems count: %s",
                    storageSystemId.toString(), unmanagedFsCount);
            _log.info(detailedStatusMessage);
        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for Isilon %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _log.error(detailedStatusMessage, e);
            throw new IsilonCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * Get all SMB shares of storagesystem
     * @param storageSystem
     * @return
     */

    private HashMap<String, HashSet<String>> discoverAllSMBShares(StorageSystem storageSystem){
        // Discover All FileShares
        String resumeToken = null;
        HashMap<String, HashSet<String>> allShares = new HashMap<String, HashSet<String>>();
        URI storageSystemId = storageSystem.getId();
        _log.info("discoverAllShares for storage system {} - start", storageSystemId);

        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonApi.IsilonList<IsilonSMBShare> isilonShares =
                                                    isilonApi.listShares(resumeToken);

                List<IsilonSMBShare> isilonSMBShareList = isilonShares.getList();
                HashSet<String> sharesHashSet = null;
                for(IsilonSMBShare share: isilonSMBShareList) {
                    //get the filesystem path and shareid
                    String path = share.getPath();
                    String shareId = share.getId();
                    sharesHashSet = allShares.get(path);
                    if(null == sharesHashSet) {
                        sharesHashSet = new HashSet<String>();
                        sharesHashSet.add(shareId);
                        allShares.put(path, sharesHashSet);
                    } else {
                        //if shares already exist for path then add
                        sharesHashSet.add(shareId);
                        allShares.put(path, sharesHashSet);
                    }

                    _log.info("Discovered SMB Share name {} and path {}", shareId, path);
                }

                resumeToken = isilonShares.getToken();
            } while(resumeToken != null);

            return allShares;
        } catch (IsilonException ie) {
            _log.error("discoverAllShares failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllShares failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        }
        catch (Exception e) {
            _log.error("discoverAllShares failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllShares failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private void validateListSizeLimitAndPersist(List<UnManagedFileSystem> newUnManagedFileSystems,
    		List<UnManagedFileSystem> existingUnManagedFileSystems, int limit){

    	if(newUnManagedFileSystems!=null && !newUnManagedFileSystems.isEmpty() &&
    			newUnManagedFileSystems.size()>= limit){
    		_partitionManager.insertInBatches(newUnManagedFileSystems,
    				Constants.DEFAULT_PARTITION_SIZE, _dbClient,
    				UNMANAGED_FILESYSTEM);
    		newUnManagedFileSystems.clear();
    	}

    	if(existingUnManagedFileSystems!=null && !existingUnManagedFileSystems.isEmpty() &&
    			existingUnManagedFileSystems.size()>= limit){
    		_partitionManager.updateInBatches(existingUnManagedFileSystems,
    				Constants.DEFAULT_PARTITION_SIZE, _dbClient,
    				UNMANAGED_FILESYSTEM);
    		existingUnManagedFileSystems.clear();
    	}
    }

    private IsilonApi.IsilonList<FileShare> discoverAllFileSystem(StorageSystem storageSystem, String resumeToken)
        throws IsilonCollectionException {

        // Discover All FileSystem
        List<FileShare> discoveredFS = new ArrayList<FileShare>();

        URI storageSystemId = storageSystem.getId();

        try {
            _log.info("discoverAllFileSystem for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            HashMap<String,FileShare> fsWithQuotaMap = new HashMap<String,FileShare>();
            // get first page of quota data, process and insert to database

            IsilonApi.IsilonList<IsilonSmartQuota> quotas = isilonApi.listQuotas(null);
            boolean qualified = false;
            for (IsilonSmartQuota quota : quotas.getList()) {
                String fsNativeId = quota.getPath();

		        qualified = isUnderUnmanagedDiscoveryPath(fsNativeId);
                if(qualified) {
		            FileShare fs = extractFileShare(fsNativeId, quota, storageSystem);
                    _log.debug("quota id {} with capacity {}", fsNativeId + ":QUOTA:" + quota.getId(),
                            fs.getCapacity() + " used capacity " + fs.getUsedCapacity());
                    fsWithQuotaMap.put(fsNativeId, fs);
                } else {
                    _log.debug("quota id {} no FileSystem or directory", fsNativeId);
                }
            }
            // get all other pages of quota data, process and set quota page by page
            while (quotas.getToken() != null && !quotas.getToken().isEmpty()) {
                quotas = isilonApi.listQuotas(quotas.getToken());
                for (IsilonSmartQuota quota : quotas.getList()) {
                    String fsNativeId = quota.getPath();

		            qualified = isUnderUnmanagedDiscoveryPath(fsNativeId);
                    if(qualified) {
                        FileShare fs = extractFileShare(fsNativeId, quota, storageSystem);
                        _log.debug("quota id {} with capacity {}", fsNativeId + ":QUOTA:" + quota.getId(),
                                fs.getCapacity() + " used capacity " + fs.getUsedCapacity());
                        fsWithQuotaMap.put(fsNativeId, fs);
                    } else {
                        _log.debug("quota id {} no FileSystem or directory", fsNativeId);
                    }
                }
            }
            _log.info("NativeGUIDGenerator for storage system {} - complete", storageSystemId);

            //Filter out FS with no Quota associated with them
            discoveredFS = new ArrayList<FileShare>(fsWithQuotaMap.values());
            IsilonApi.IsilonList<FileShare> isilonFSList = new IsilonApi.IsilonList<FileShare>();
            isilonFSList.addList(discoveredFS);
            //isilonFSList.setToken(isilonFileSystems.getToken());
            return isilonFSList;

        } catch (IsilonException ie) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllFileSystem failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        }
        catch (Exception e) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllFileSystem failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private FileShare extractFileShare(String fsNativeId, IsilonSmartQuota quota, StorageSystem storageSystem){

        _log.debug("extractFileShare for {} and quota {} ", fsNativeId, quota.toString());
      	FileShare fs = new FileShare();

	    String[] splits = fsNativeId.split("/");
        if(splits.length > 0){
            fs.setName(splits[splits.length - 1]);
        }

        fs.setMountPath(fsNativeId);
        fs.setNativeId(fsNativeId);
        fs.setExtensions(new StringMap());

        String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem.getSystemType(),
                            storageSystem.getSerialNumber(), fsNativeId);

        fs.setNativeGuid(fsNativeGuid);
        fs.setPath(fsNativeId);
        long capacity = 0;
        if (quota.getThresholds() != null && quota.getThresholds().getHard() != null) {
            capacity = quota.getThresholds().getHard();
        }
        fs.setCapacity(capacity);
        fs.setUsedCapacity(quota.getUsagePhysical());
        if(!quota.getId().equalsIgnoreCase("null")) {
            fs.getExtensions().put(QUOTA, quota.getId());
        }
	    return fs;
    }

    private boolean isUnderUnmanagedDiscoveryPath(String fsNativeId){
        boolean qualified = false;

    	for(String discPath : _discPathsForUnManaged){
        	if(fsNativeId.startsWith(discPath)){
                    qualified = true;
                    break;
                }
        }
        return qualified;
    }

    private HashMap<String, HashSet<Integer>> discoverAllExports(StorageSystem storageSystem)
            throws IsilonCollectionException {

        // Discover All FileSystem
        HashMap<String, HashSet<Integer>> allExports = new HashMap<String, HashSet<Integer>>();

        URI storageSystemId = storageSystem.getId();

        String resumeToken = null;

        try {
            _log.info("discoverAllExports for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            do {
                IsilonApi.IsilonList<IsilonExport> isilonExports  = isilonApi.listExports(resumeToken);

                List<IsilonExport> exports = isilonExports.getList();

                for(IsilonExport exp : exports){
                    _log.info("Discovered fS export {}", exp.toString());
                    HashSet<Integer> exportIds = new HashSet<Integer>();
                    for(String path : exp.getPaths()) {
                        exportIds = allExports.get(path);
                        if(exportIds == null) {
                            exportIds = new HashSet<Integer>();
                        }
                        exportIds.add(exp.getId());
                        allExports.put(path, exportIds);
                        _log.debug("Discovered fS put export Path {} Export id {}", path, exportIds.size() +":" + exportIds);
                    }
                }
                resumeToken = isilonExports.getToken();
            } while(resumeToken != null);

            return allExports;

        } catch (IsilonException ie) {
            _log.error("discoverAllExports failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllExports failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        }
        catch (Exception e) {
            _log.error("discoverAllExports failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllExports failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private IsilonExport getIsilonExport(IsilonApi isilonApi, Integer expId) {
        IsilonExport exp = null;
        try {
            _log.debug("call getIsilonExport for {} ", expId);
            if(expId != null) {
                exp = isilonApi.getExport(expId.toString());
                _log.debug("call getIsilonExport {}", exp.toString());
            }
        }catch(Exception e){
            _log.error("Exception while getting Export for {}", expId);
        }
        return exp;
    }

    /**
     * get UnManaged Cifs Shares and their ACLs
     * @param unManagedFileSystem
     * @param smbShares
     * @param unManagedCifsShareACLList
     * @param fsPath
     * @param isilonApi
     */
    private void getUnmanagedCifsShareACL(UnManagedFileSystem unManagedFileSystem,
                                          HashSet<String> smbShares,
                                          List<UnManagedCifsShareACL> unManagedCifsShareACLList,
                                          StoragePort storagePort,
                                          String fsname,
                                          IsilonApi isilonApi){
        _log.info("getUnmanagedCifsShareACL for UnManagedFileSystem file path{} - start", fsname);

        //HashSet<String> smbShares = allShares.get(fsPath);
        if(null != smbShares && !smbShares.isEmpty() ){
            UnManagedSMBShareMap unManagedSmbShareMap = null;
            if( null == unManagedFileSystem.getUnManagedSmbShareMap()){
                unManagedSmbShareMap = new UnManagedSMBShareMap(); //initialise
                unManagedFileSystem.setUnManagedSmbShareMap(unManagedSmbShareMap);
            }
            unManagedSmbShareMap = unManagedFileSystem.getUnManagedSmbShareMap();
            UnManagedSMBFileShare unManagedSMBFileShare = null;

            for(String shareId : smbShares){
                //get smb share details
                IsilonSMBShare isilonSMBShare = getIsilonSMBShare(isilonApi, shareId);
                if(null != isilonSMBShare) {
                    unManagedSMBFileShare = new UnManagedSMBFileShare();
                    unManagedSMBFileShare.setName(isilonSMBShare.getName());
                    unManagedSMBFileShare.setDescription(isilonSMBShare.getDescription());
                    unManagedSMBFileShare.setNativeId(unManagedFileSystem.getNativeGuid());
                    unManagedSMBFileShare.setMountPoint("\\\\" + storagePort.getPortNetworkId() + "\\" + isilonSMBShare.getName());
                    unManagedSMBFileShare.setPath(isilonSMBShare.getPath());
                    unManagedSMBFileShare.setMaxUsers(-1);
                    //setting the dummy permission.This is not used by isilon, but used by other storage system
                    unManagedSMBFileShare.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);
                    unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);

                    //set Unmanaged SMB Share
                    unManagedSmbShareMap.put(fsname, unManagedSMBFileShare);
                    _log.info("smb share name {} and fs mount point {} ", unManagedSMBFileShare.getName(), unManagedSMBFileShare.getMountPoint());
                    //process ACL permission
                    UnManagedCifsShareACL unManagedCifsShareACL = null;
                    List<IsilonSMBShare.Permission> permissionList = isilonSMBShare.getPermissions();
                    for (IsilonSMBShare.Permission permission : permissionList) {
                    	//Isilon can have deny permission type. Do not ingest the ACL for deny
                    	
                    	if(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW
                    			.equalsIgnoreCase(permission.getPermissionType())) {
                    		
                    		unManagedCifsShareACL = new UnManagedCifsShareACL();
                    		//share name
                    		unManagedCifsShareACL.setShareName(isilonSMBShare.getName());
                    		//permission
                    		unManagedCifsShareACL.setPermission(permission.getPermission());

                    		//we take only username and we can ignore type and id
                    		//user
                    		unManagedCifsShareACL.setUser(permission.getTrustee().getName());

                    		//filesystem id
                    		unManagedCifsShareACL.setFileSystemId(unManagedFileSystem.getId());
                    		unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));

                    		unManagedCifsShareACLList.add(unManagedCifsShareACL);
                    		_log.info("isilonSMBShare details permission {}- ", permission.toString());
                    	}
                    }
                }
            }
            _log.info("File System id {} and Number of shares : {}", unManagedFileSystem.getId(), unManagedSmbShareMap.size());

        }
        return ;
    }

    @Override
    public void scan(AccessProfile arg0) throws BaseCollectionException {
        // TODO Auto-generated method stub
    }

    /**
     * If discovery fails, then mark the system as unreachable.  The
     * discovery framework will remove the storage system from the database.
     *
     * @param system  the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.persistObject(system);
        } catch (DatabaseException e) {
            _log.error("discoverStorage failed.  Failed to update discovery status to ERROR.", e);
        }

    }


    /**
     * create StorageFileSystem Info Object
     *
     * @param unManagedFileSystem
     * @param unManagedFileSystemNativeGuid
     * @param storageSystem
     * @param fileSystem
     * @return UnManagedFileSystem
     * @throws IOException
     * @throws IsilonCollectionException
     */
    private UnManagedFileSystem createUnManagedFileSystem(
            UnManagedFileSystem unManagedFileSystem,
            String unManagedFileSystemNativeGuid, StorageSystem storageSystem,
            StoragePool pool, StoragePort storagePort, FileShare fileSystem)
            throws IOException, IsilonCollectionException {

        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(storageSystem.getId());
            if(null != pool) {
                unManagedFileSystem.setStoragePoolUri(pool.getId());
            }
        }

        if(null == unManagedFileSystem.getExtensions()) {
            unManagedFileSystem.setExtensions(new StringMap());
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                        .toString(), TRUE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(), TRUE);

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            StringSet matchedVPools =  DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId(),
            		unManagedFileSystemCharacteristics.get(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
            				.toString()));
            if (unManagedFileSystemInformation.containsKey(UnManagedFileSystem.SupportedFileSystemInformation.
                    SUPPORTED_VPOOL_LIST.toString())) {

                if (null != matchedVPools && matchedVPools.isEmpty()) {
                    // replace with empty string set doesn't work, hence added explicit code to remove all
                    unManagedFileSystemInformation.get(
                             SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).clear();
                } else {
                    // replace with new StringSet
                    unManagedFileSystemInformation.get(
                         SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString()).replace( matchedVPools);
                 _log.info("Replaced Pools :"+Joiner.on("\t").join( unManagedFileSystemInformation.get(
                         SupportedVolumeInformation.SUPPORTED_VPOOL_LIST.toString())));
                }
            } else {
                unManagedFileSystemInformation
                .put(UnManagedFileSystem.SupportedFileSystemInformation.SUPPORTED_VPOOL_LIST
                        .toString(), matchedVPools);
            }

        }

        if(null != storagePort){
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(), TRUE);
        if (null != storageSystem) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(storageSystem.getSystemType());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        //Set attributes of FileSystem
        StringSet fsPath = new StringSet();
        fsPath.add(fileSystem.getNativeId());

        StringSet fsMountPath = new StringSet();
        fsMountPath.add(fileSystem.getMountPath());

        StringSet fsName = new StringSet();
        fsName.add(fileSystem.getName());

        StringSet fsId = new StringSet();
        fsId.add(fileSystem.getNativeId());

        unManagedFileSystem.setLabel(fileSystem.getName());

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


        StringSet provisionedCapacity = new StringSet();
        long capacity = 0;
        if(fileSystem.getCapacity() != null) {
            capacity = fileSystem.getCapacity();
        }
        provisionedCapacity.add(String.valueOf(capacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(), provisionedCapacity);

        StringSet allocatedCapacity = new StringSet();
        long usedCapacity = 0;
        if(fileSystem.getUsedCapacity() != null) {
        	usedCapacity = fileSystem.getUsedCapacity();
        }
        allocatedCapacity.add(String.valueOf(usedCapacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(), allocatedCapacity);

        String quotaId = fileSystem.getExtensions().get(QUOTA);
        if(quotaId != null) {
            unManagedFileSystem.getExtensions().put(QUOTA, quotaId);
        }
        _log.debug("Quota : {}  : {}", quotaId, fileSystem.getPath());

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .addFileSystemCharacterstcis(unManagedFileSystemCharacteristics);

        //Initialize ExportMap
        unManagedFileSystem.setFsUnManagedExportMap(new UnManagedFSExportMap());

        //Initialize SMBMap
        unManagedFileSystem.setUnManagedSmbShareMap(new UnManagedSMBShareMap());

        return unManagedFileSystem;
    }

    /**
     * get share details
     * @param isilonApi
     * @param shareId
     * @return
     */
    private IsilonSMBShare getIsilonSMBShare(IsilonApi isilonApi, String shareId) {
        _log.debug("call getIsilonSMBShare for {} ", shareId);
        IsilonSMBShare isilonSMBShare = null;
        try {
            if(isilonApi != null) {
                isilonSMBShare = isilonApi.getShare(shareId);
                _log.debug("call getIsilonSMBShare {}", isilonSMBShare.toString());
            }
        }catch(Exception e){
            _log.error("Exception while getting SMBShare for {}", shareId);
        }
        return isilonSMBShare;
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
        	if(fileShare!=null && !fileShare.getInactive()){
        		return true;
        	}
        }
        return false;
    }

    /**
     * check Pre Existing Storage filesystem exists in DB
     *
     * @param nativeGuid
     * @return unManageFileSystem
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

        for (URI fileSystemURI : filesystemUris){
        	 filesystemInfo = _dbClient.queryObject(UnManagedFileSystem.class,
        			 fileSystemURI);
        	 if(filesystemInfo!=null && !filesystemInfo.getInactive()){
        		return filesystemInfo;
        	 }
        }
           
        return null;

    }

    /**
     * Check if Pool exists in DB.
     *
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    protected StoragePool checkStoragePoolExistsInDB(String nativeGuid)
            throws IOException {
        StoragePool pool = null;
        // use NativeGuid to lookup Pools in DB
        @SuppressWarnings("deprecation")
        List<URI> poolURIs = _dbClient
                .queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(nativeGuid));
        for (URI poolURI:poolURIs){
        	pool = _dbClient.queryObject(StoragePool.class, poolURI);
        	if(pool !=null && !pool.getInactive()){
        		return pool;
        	}
        }
        return null;
    }

    /*
     * get Storage Pool
     * @return
     */
    private StoragePool getStoragePool(StorageSystem storageSystem) throws IOException {
        StoragePool storagePool = null;
        // Check if storage pool was already discovered
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        while(storagePoolIter.hasNext()){
            URI storagePoolURI = storagePoolIter.next();
            storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
            if(storagePool!=null && !storagePool.getInactive()){
            	_log.debug("found a pool for storage system  {} {}",
                        storageSystem.getSerialNumber(), storagePool);
            	return storagePool;
            }
        }
        return null;
    }

    private StoragePort getStoragePortPool(StorageSystem storageSystem)
            throws IOException {
        StoragePort storagePort = null;
        // Check if storage port was already discovered
        URIQueryResultList storagePortURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePortConstraint(storageSystem.getId()),
                storagePortURIs);
        Iterator<URI> storagePortIter = storagePortURIs.iterator();
        while(storagePortIter.hasNext()){
            URI storagePortURI = storagePortIter.next();
            storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if(storagePort!=null && !storagePort.getInactive()){
            	_log.debug("found a port for storage system  {} {}",
                        storageSystem.getSerialNumber(), storagePort);
            	return storagePort;
            }
        }
        return null;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     *    Ignore exports with multiple exports for the same path
     *    Ignore exports that have multiple security flavors
     *    Ignore exports with multiple paths
     *    Ignore exports not found on the array
     *    Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     *
     * @param umfs
     * @param expIdMap
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private List<UnManagedFileExportRule> getUnManagedFSExportRules(UnManagedFileSystem umfs, HashMap<String, HashSet<Integer>>expIdMap,
                                            StoragePort storagePort, String fsPath, IsilonApi isilonApi) {
        List<UnManagedFileExportRule> exportRules = new ArrayList<UnManagedFileExportRule>();
        UnManagedExportVerificationUtility validationUtility =
                                    new UnManagedExportVerificationUtility(_dbClient);
        List<UnManagedFileExportRule> exportRulesTemp = null;
        boolean isAllRulesValid = true;
        for(String expMapPath:expIdMap.keySet()) {
            HashSet<Integer> isilonExportIds = new HashSet<>();
            _log.info("getUnManagedFSExportMap {} : export ids : {}",
                                                expMapPath, expIdMap.get(expMapPath));
            isilonExportIds = expIdMap.get(expMapPath);
            if(isilonExportIds != null && !isilonExportIds.isEmpty()) {
                exportRulesTemp = getUnManagedFSExportRules(umfs, storagePort,
                                                isilonExportIds, expMapPath, isilonApi);
                //validate export rules for each path
                if(null != exportRulesTemp && !exportRulesTemp.isEmpty()) {
                    isAllRulesValid = validationUtility.validateUnManagedExportRules(
                                                                exportRulesTemp, false);
                    if(isAllRulesValid) {
                        _log.info("Validating rules success for export {}", expMapPath);
                        exportRules.addAll(exportRulesTemp);
                    } else {
                        _log.info("Ignroing the rules for export {}", expMapPath);
                        isAllRulesValid = false;
                    }
                }
            }
        }

        if(exportRules.isEmpty() || false == isAllRulesValid) {
            umfs.setHasExports(false);
            _log.info("FileSystem " + fsPath + " does not have valid ViPR exports ");
            exportRules.clear();
        }
        return exportRules;
    }
    
    /**
     * Generate Export Map for UnManagedFileSystem
     *    Ignore exports with multiple exports for the same path
     *    Ignore exports that have multiple security flavors
     *    Ignore exports with multiple paths
     *    Ignore exports not found on the array
     *    Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     *
     * @param umfs
     * @param expIdMap
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private boolean getUnManagedFSExportMap(UnManagedFileSystem umfs, HashMap<String, HashSet<Integer>>expIdMap,
                                            StoragePort storagePort, String fsPath, IsilonApi isilonApi) {

        UnManagedFSExportMap exportMap = new UnManagedFSExportMap();

        boolean validExports = false;

        for(String expMapPath:expIdMap.keySet()) {
            HashSet<Integer> isilonExportIds = new HashSet<>();
            _log.info("getUnManagedFSExportMap {} : export ids : {}", expMapPath, expIdMap.get(expMapPath));
            isilonExportIds = expIdMap.get(expMapPath);
            if(isilonExportIds != null && !isilonExportIds.isEmpty()) {
                validExports = getUnManagedFSExportMap(umfs, isilonExportIds,
                        storagePort, expMapPath, isilonApi);
            } else {
                validExports = false;
            }
            if(!validExports) {
                //perform resetting umfs export map
                umfs.setFsUnManagedExportMap(exportMap);
                return false;
            }
        }
        return true;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     *    Ignore exports with multiple exports for the same path
     *    Ignore exports that have multiple security flavors
     *    Ignore exports with multiple paths
     *    Ignore exports not found on the array
     *    Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     *
     * @param umfs
     * @param isilonExportIds
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private boolean getUnManagedFSExportMap(UnManagedFileSystem umfs, HashSet<Integer> isilonExportIds,
                                            StoragePort storagePort, String fsPath, IsilonApi isilonApi) {

        UnManagedFSExportMap exportMap = new UnManagedFSExportMap();

        int generatedExportCount = 0;

        ArrayList<IsilonExport> isilonExports = new ArrayList<IsilonExport>();

        if( isilonExportIds != null && isilonExportIds.size() > 1) {
            _log.info("Ignoring file system {}, Multiple exports found {} ", fsPath, isilonExportIds.size());
            return false;
        }

        for(Integer expId : isilonExportIds) {
            IsilonExport exp = getIsilonExport(isilonApi, expId);
            if(exp == null) {
                _log.info("Ignoring file system {}, export {} not found", fsPath, expId);
                return false;
            } else if(exp.getSecurityFlavors().size() > 1) {
                _log.info("Ignoring file system {}, multiple security flavors {} found", fsPath, exp.getSecurityFlavors().toString());
                return false;
            } else if(exp.getPaths().size() > 1) {
                _log.info("Ignoring file system {}, multiple paths {} found", fsPath, exp.getPaths().toString());
                return false;
            } else {
                isilonExports.add(exp);
            }
        }

        for(IsilonExport exp : isilonExports) {
            String securityFlavor = exp.getSecurityFlavors().get(0);
            //Isilon Maps sys to unix and we do this conversion during export from ViPR
            if(securityFlavor.equalsIgnoreCase(UNIXSECURITY))
                securityFlavor = SYSSECURITY;

            String path = exp.getPaths().get(0);

            //Get User
            String rootUserMapping = "";
            String mapAllUserMapping = "";
            if(exp.getMap_root() != null && exp.getMap_root().getUser() != null) {
                rootUserMapping = exp.getMap_root().getUser();
            } else if (exp.getMap_all() != null && exp.getMap_all().getUser() != null) {
                mapAllUserMapping = exp.getMap_all().getUser();
            }

            String resolvedUser = (rootUserMapping != null && (!rootUserMapping.isEmpty())) ? rootUserMapping : mapAllUserMapping;

            if(exp != null && exp.getReadOnlyClients() != null && !exp.getReadOnlyClients().isEmpty()) {
                UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                        exp.getReadOnlyClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor, RO,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedROFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                generatedExportCount++;
            }

            if(exp != null && exp.getReadWriteClients() != null && !exp.getReadWriteClients().isEmpty()) {
                UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                        exp.getReadWriteClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor,  RW,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedRWFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedRWFSExport.getFileExportKey(), unManagedRWFSExport);
                generatedExportCount++;
            }

            if(exp != null && exp.getRootClients() != null && !exp.getRootClients().isEmpty()) {
                UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                        exp.getRootClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor, ROOT,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        path);
                unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                generatedExportCount++;
            }

            if(exp.getReadOnlyClients() != null && exp.getReadWriteClients() != null && exp.getRootClients() != null) {
                //Check Clients size
                if(exp.getReadOnlyClients().isEmpty() && exp.getReadWriteClients().isEmpty() && exp.getRootClients().isEmpty() )  {
                    //All hosts case.  Check whether it is RO/RW/ROOT

                    if(exp.getReadOnly()) {
                        //This is a read only export for all hosts
                        UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                                exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                securityFlavor, RO,
                                rootUserMapping, NFS, storagePort.getPortName(), path,
                                path);
                        unManagedROFSExport.setIsilonId(exp.getId().toString());
                        exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                        generatedExportCount++;
                    } else {
                        //Not read Only case
                        if(exp.getMap_all() != null && exp.getMap_all().getUser() != null && exp.getMap_all().getUser().equalsIgnoreCase(ROOT)) {
                            //All hosts with root permission
                            UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    securityFlavor, ROOT,
                                    mapAllUserMapping, NFS, storagePort.getPortName(), path,
                                    path);
                            unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                            exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                            generatedExportCount++;

                        } else if(exp.getMap_all() != null) {
                            //All hosts with RW permission
                            UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    securityFlavor,  RW,
                                    rootUserMapping, NFS, storagePort.getPortName(), path,
                                    path);
                            unManagedRWFSExport.setIsilonId(exp.getId().toString());
                            exportMap.put(unManagedRWFSExport.getFileExportKey(), unManagedRWFSExport);
                            generatedExportCount++;
                        }
                    }
                }
            }
        }

        if( exportMap.values().size() < generatedExportCount) {
            //The keys are not unique and so all the exports are not valid
            _log.info("Ignoring Exports because they have multiple exports with the same internal export key <sec, perm, root-mapping>. Expected {} got {}",
                    generatedExportCount, exportMap.values().size());
            return false;
        }

        //Return valid
        UnManagedFSExportMap allExportMap = umfs.getFsUnManagedExportMap();
        if(allExportMap == null) {
            allExportMap = new UnManagedFSExportMap();
        }
        allExportMap.putAll(exportMap);
        umfs.setFsUnManagedExportMap(allExportMap);

        return true;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     *    Ignore exports with multiple exports for the same path
     *    Ignore exports that have multiple security flavors
     *    Ignore exports with multiple paths
     *    Ignore exports not found on the array
     *    Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     *
     * @param umfs
     * @param isilonExportIds
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private List<UnManagedFileExportRule> getUnManagedFSExportRules(UnManagedFileSystem umfs, StoragePort storagePort, 
    		HashSet<Integer> isilonExportIds, String fsPath, IsilonApi isilonApi) {

    	List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();
    	ArrayList<IsilonExport> isilonExports = new ArrayList<IsilonExport>();

        if( isilonExportIds != null && isilonExportIds.size() > 1) {
            _log.info("Ignoring file system {}, Multiple exports found {} ", fsPath, isilonExportIds.size());
        }

        for(Integer expId : isilonExportIds) {
            IsilonExport exp = getIsilonExport(isilonApi, expId);
            if(exp == null) {
                _log.info("Ignoring file system {}, export {} not found", fsPath, expId);
            } else if(exp.getSecurityFlavors().size() > 1) {
                _log.info("Ignoring file system {}, multiple security flavors {} found", fsPath, exp.getSecurityFlavors().toString());
            } else if(exp.getPaths().size() > 1) {
                _log.info("Ignoring file system {}, multiple paths {} found", fsPath, exp.getPaths().toString());
            } else {
                isilonExports.add(exp);
            }
        }

        for(IsilonExport exp : isilonExports) {
            String securityFlavor = exp.getSecurityFlavors().get(0);
            //Isilon Maps sys to unix and we do this conversion during export from ViPR
            if(securityFlavor.equalsIgnoreCase(UNIXSECURITY))
                securityFlavor = SYSSECURITY;

            String path = exp.getPaths().get(0);

            //Get User
            String rootUserMapping = "";
            String mapAllUserMapping = "";
            if(exp.getMap_root() != null && exp.getMap_root().getUser() != null) {
                rootUserMapping = exp.getMap_root().getUser();
            } else if (exp.getMap_all() != null && exp.getMap_all().getUser() != null) {
                mapAllUserMapping = exp.getMap_all().getUser();
            }

            String resolvedUser = (rootUserMapping != null && (!rootUserMapping.isEmpty())) ? rootUserMapping : mapAllUserMapping;

        	UnManagedFileExportRule expRule = new UnManagedFileExportRule();
        	expRule.setExportPath(path);
        	expRule.setSecFlavor(securityFlavor);
        	expRule.setAnon(resolvedUser);
        	expRule.setDeviceExportId(exp.getId().toString());
        	expRule.setFileSystemId(umfs.getId());
        	expRule.setMountPoint(storagePort.getPortNetworkId() + ":" + fsPath);
        	
            if(exp != null && exp.getReadOnlyClients() != null && !exp.getReadOnlyClients().isEmpty()) {
            	expRule.setReadOnlyHosts(new StringSet(exp.getReadOnlyClients()));
            }

            if(exp != null && exp.getReadWriteClients() != null && !exp.getReadWriteClients().isEmpty()) {
            	expRule.setReadWriteHosts(new StringSet(exp.getReadWriteClients()));
            }

            if(exp != null && exp.getRootClients() != null && !exp.getRootClients().isEmpty()) {
            	expRule.setRootHosts(new StringSet(exp.getRootClients()));
            }

            if(exp.getReadOnlyClients() != null && exp.getReadWriteClients() != null && exp.getRootClients() != null) {
                //Check Clients size
                if(exp.getReadOnlyClients().isEmpty() && exp.getReadWriteClients().isEmpty() && exp.getRootClients().isEmpty())  {
                    //All hosts case.  Check whether it is RO/RW/ROOT

                    if(exp.getReadOnly()) {
                        //This is a read only export for all hosts
                    	expRule.setReadOnlyHosts(new StringSet(exp.getClients()));
                    } else {
                        //Not read Only case
                        if(exp.getMap_all() != null && exp.getMap_all().getUser() != null && exp.getMap_all().getUser().equalsIgnoreCase(ROOT)) {
                            //All hosts with root permission
                        	expRule.setRootHosts(new StringSet(exp.getClients()));

                        } else if(exp.getMap_all() != null) {
                            //All hosts with RW permission
                        	expRule.setReadWriteHosts(new StringSet(exp.getClients()));
                        }
                    }
                }
            }
            expRules.add(expRule);
        }        

        return expRules;
    }
       
    private HashMap<String, HashSet<Integer>> getExportsIncludingSubDir(String fsPath, HashMap<String, HashSet<Integer>> expMap){
        HashMap<String, HashSet<Integer>> expMapWithIds = new HashMap<>();
        for(String expPath:expMap.keySet()) {
            if(expPath.equalsIgnoreCase(fsPath) || expPath.contains(fsPath + "/")) {
                HashSet<Integer> expIds = expMap.get(expPath);
                if(expIds == null) {
                    expIds = new HashSet<>();
                }
                expIds.addAll(expIds);
                expMapWithIds.put(expPath, expIds);
            }
        }
        return expMapWithIds;
    }


    private HashMap<String, HashMap<String, HashSet<Integer>>> getExportsWithSubDirForFS(List<FileShare> discoveredIsilonFS,
                                                                        HashMap<String, HashSet<Integer>> expMap){
        HashMap<String, HashMap<String, HashSet<Integer>>> expMapTree = new HashMap<>();
        for(FileShare fs:discoveredIsilonFS) {
            expMapTree.put(fs.getPath(), getExportsIncludingSubDir(fs.getPath(), expMap));

        }
        return expMapTree;
    }

}
