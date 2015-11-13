/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualNAS.VirtualNasState;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonAccessZone;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApi.IsilonList;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonClusterConfig;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonNetworkPool;
import com.emc.storageos.isilon.restapi.IsilonSMBShare;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfo;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfoV2;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.isilon.restapi.IsilonSnapshot;
import com.emc.storageos.isilon.restapi.IsilonSshApi;
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
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Class for Isilon discovery and collecting stats from Isilon storage device
 */
public class IsilonCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private final Logger _log = LoggerFactory.getLogger(IsilonCommunicationInterface.class);
    private static final String POOL_TYPE = "IsilonNodePool";
    private static final int BYTESCONVERTER = 1024;
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
    private static final String CIFS = "CIFS";
    private static final String UNIXSECURITY = "unix";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String SYSSECURITY = "sys";
    private static final String NFSv4 = "NFSv4";

    private static final Long MAX_NFS_EXPORTS_V7_2 = 1500L;
    private static final Long MAX_CIFS_SHARES = 40000L;
    private static final Long MAX_STORAGE_OBJECTS = 40000L;
    private static final String SYSTEM_ACCESS_ZONE_NAME = "System";
    private static final Long GB_IN_BYTES = 1073741824L;
    private static final String ONEFS_V8 = "8.0.0.0";
    private static final String ONEFS_V7_2 = "7.2.0.0";

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
     * @param isilonCluster StorageDevice object
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

            // compute static load processor code
            computeStaticLoadMetrics(storageSystemId);

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

    private void computeStaticLoadMetrics(final URI storageSystemId) throws BaseCollectionException {
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);

        _log.info("started computeStaticLoadMetrics for storagesystem: {}", storageSystem.getLabel());
        StringMap dbMetrics = null;
        String accessZoneId = null;
        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            VirtualNAS virtualNAS = null;
            // //step-1 process the dbmetrics for user define access zones
            List<IsilonAccessZone> accessZoneList = isilonApi.getAccessZones(null);
            for (IsilonAccessZone isAccessZone : accessZoneList) {
                accessZoneId = isAccessZone.getZone_id().toString();
                // get the total fs count and capacity for AZ
                if (isAccessZone.isSystem() != true) {
                    virtualNAS = findvNasByNativeId(storageSystem, accessZoneId);
                    if (virtualNAS != null) {
                        _log.info("Process db metrics for access zone : {}", isAccessZone.getName());
                        dbMetrics = virtualNAS.getMetrics();
                        if (dbMetrics == null) {
                            dbMetrics = new StringMap();
                        }
                        // process db metrics
                        populateDbMetricsAz(isAccessZone, isilonApi, dbMetrics);

                        // set AZ dbMetrics in db
                        virtualNAS.setMetrics(dbMetrics);
                        _dbClient.updateObject(virtualNAS);
                    }
                } else {
                    PhysicalNAS physicalNAS = findPhysicalNasByNativeId(storageSystem, accessZoneId);
                    if (physicalNAS == null) {
                        _log.error(String.format("computeStaticLoadMetrics is failed for  Storagesystemid: %s", storageSystemId));
                        return;
                    }
                    dbMetrics = physicalNAS.getMetrics();
                    if (dbMetrics == null) {
                        dbMetrics = new StringMap();
                    }
                    /* process the system accesszone dbmetrics */
                    _log.info("process db metrics for access zone : {}", isAccessZone.getName());
                    populateDbMetricsAz(isAccessZone, isilonApi, dbMetrics);
                    physicalNAS.setMetrics(dbMetrics);
                    _dbClient.updateObject(physicalNAS);
                }
            }
        } catch (Exception e) {
            _log.error("CollectStatisticsInformation failed. Storage system: " + storageSystemId, e);
        }
    }

    /**
     * process dbmetrics for total count and capacity
     * 
     * @param azName
     * @param isilonApi
     * @param dbMetrics
     */
    private void populateDbMetricsAz(final IsilonAccessZone accessZone, IsilonApi isilonApi, StringMap dbMetrics) {

        long totalProvCap = 0L;
        long totalFsCount = 0L;
        String resumeToken = null;
        String zoneName = accessZone.getName();
        String baseDirPath = accessZone.getPath() + "/";

        // filesystems count & Capacity
        IsilonList<IsilonSmartQuota> quotas = null;

        do {
            quotas = isilonApi.listQuotas(resumeToken, baseDirPath);

            if (quotas != null && !quotas.getList().isEmpty()) {
                for (IsilonSmartQuota quota : quotas.getList()) {
                    if (quota.getThresholds() != null && quota.getThresholds().getHard() != null) {
                        totalProvCap = totalProvCap + quota.getThresholds().getHard();
                        totalFsCount++;
                    }
                }
                resumeToken = quotas.getToken();
            }
        } while (resumeToken != null);

        //get the base dir paths
        List<String> baseDirPaths = null;
        if (baseDirPath.equals(IFS_ROOT)) {
            List<IsilonAccessZone> isilonAccessZoneList = isilonApi.getAccessZones(resumeToken);
            baseDirPaths = new ArrayList<String>();
            for (IsilonAccessZone isiAccessZone: isilonAccessZoneList) {
                if (isiAccessZone.isSystem() == false) {
                    baseDirPaths.add(isiAccessZone.getPath() + "/");
                }
            }
        }
        //snapshots count & snap capacity
        resumeToken = null;
        IsilonList<IsilonSnapshot> snapshots = null;
        do {
            snapshots = isilonApi.listSnapshots(resumeToken);
            if (snapshots != null && !snapshots.getList().isEmpty()) {
                if (!baseDirPath.equals(IFS_ROOT)) { //if it not system access zone then compare with fs path with base dir path
                    _log.info("access zone base directory path {}", baseDirPath);
                    for (IsilonSnapshot isilonSnap: snapshots.getList()) {
                        if (isilonSnap.getPath().startsWith(baseDirPath)) {
                            totalProvCap = totalProvCap + Long.valueOf(isilonSnap.getSize());
                            totalFsCount ++;
                        }
                    }
                } else {//process the snapshots for system access zone
                    boolean snapSystem = true;
                    for (IsilonSnapshot isilonSnap: snapshots.getList()) {
                        snapSystem = true;
                        //first check fs path with user defined AZ's paths
                        if (baseDirPaths != null && !baseDirPaths.isEmpty()) {
                            for (String basePath : baseDirPaths) {
                                if (isilonSnap.getPath().startsWith(basePath)) {
                                    snapSystem = false;
                                    break;
                                }
                            }
                        }
                        //if it not matched with any user define AZ's then it is belongs system AZ
                        if (snapSystem) {
                            totalProvCap = totalProvCap + Long.valueOf(isilonSnap.getSize());
                            totalFsCount ++;
                            _log.info("System access zone base directory path: {}", accessZone.getPath());

                        }
                    }
                }
                resumeToken = snapshots.getToken();
               }
        } while (resumeToken != null);

        _log.info("Total fs Count {} for access zone : {}", String.valueOf(totalFsCount), accessZone.getName());
        _log.info("Total fs Capacity {} for access zone : {}", String.valueOf(totalProvCap), accessZone.getName());

        // get total exports
        int nfsExportsCount = 0;
        int cifsSharesCount = 0;
        resumeToken = null;
        IsilonList<IsilonExport> isilonNfsExports = null;
        do {
            isilonNfsExports = isilonApi.listExports(resumeToken, zoneName);
            if (isilonNfsExports != null) {
                nfsExportsCount = nfsExportsCount + isilonNfsExports.size();
                resumeToken = isilonNfsExports.getToken();
            }
        } while (resumeToken != null);
        _log.info("Total NFS exports {} for access zone : {}", String.valueOf(nfsExportsCount), accessZone.getName());

        // get cifs exports for given access zone
        resumeToken = null;
        IsilonList<IsilonSMBShare> isilonCifsExports = null;
        do {
            isilonCifsExports = isilonApi.listShares(resumeToken, zoneName);
            if (isilonCifsExports != null) {
                cifsSharesCount = cifsSharesCount + isilonCifsExports.size();
                resumeToken = isilonCifsExports.getToken();
            }
        } while (resumeToken != null);
        _log.info("Total CIFS sharess {} for access zone : {}", String.valueOf(cifsSharesCount), accessZone.getName());

        if (dbMetrics == null) {
            dbMetrics = new StringMap();
        }
        // set total nfs and cifs exports for give AZ
        dbMetrics.put(MetricsKeys.totalNfsExports.name(), String.valueOf(nfsExportsCount));
        dbMetrics.put(MetricsKeys.totalCifsShares.name(), String.valueOf(cifsSharesCount));
        // set total fs objects and their sum of capacity for give AZ
        dbMetrics.put(MetricsKeys.storageObjects.name(), String.valueOf(totalFsCount));
        dbMetrics.put(MetricsKeys.usedStorageCapacity.name(), String.valueOf(totalProvCap));

        Long maxExports = MetricsKeys.getLong(MetricsKeys.maxNFSExports, dbMetrics) +
                MetricsKeys.getLong(MetricsKeys.maxCifsShares, dbMetrics);
        Long maxStorObjs = MetricsKeys.getLong(MetricsKeys.maxStorageObjects, dbMetrics);
        Long maxCapacity = MetricsKeys.getLong(MetricsKeys.maxStorageCapacity, dbMetrics);

        Long totalExports = Long.valueOf(nfsExportsCount + cifsSharesCount);
        // setting overLoad factor (true or false)
        String overLoaded = FALSE;
        if (totalExports >= maxExports || totalProvCap >= maxCapacity || totalFsCount >= maxStorObjs) {
            overLoaded = TRUE;
        }

        double percentageLoadExports = 0.0;
        // percentage calculator
        if (totalExports > 0.0) {
            percentageLoadExports = ((double) (totalExports) / maxExports) * 100;
        }
        double percentageLoadStorObj = ((double) (totalProvCap) / maxCapacity) * 100;
        double percentageLoad = (percentageLoadExports + percentageLoadStorObj) / 2;

        dbMetrics.put(MetricsKeys.percentLoad.name(), String.valueOf(percentageLoad));
        dbMetrics.put(MetricsKeys.overLoaded.name(), overLoaded);
        return;
    }

    /**
     * Dump records on disk & persist the records in db.
     */
    private void persistStatsInDB(List<Stat> stats) throws BaseCollectionException {
        if (!stats.isEmpty()) {
            _keyMap.put(Constants._Stats, stats);
            dumpStatRecords();
            // Persist in db after processing the paged data.
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
                accessProfile.getIpAddress() + ":" + accessProfile.getPortNumber(),
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
            if (!storageSystem.getReachableStatus()) {
                throw new IsilonCollectionException("Failed to connect to " + storageSystem.getIpAddress());
            }
            _completer.statusPending(_dbClient, "Completed cluster discovery");
            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            // discover pools
            Map<String, List<StoragePool>> pools = discoverPools(storageSystem, poolsToMatchWithVpool);
            _log.info("No of newly discovered pools {}", pools.get(NEW).size());
            _log.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if (!pools.get(NEW).isEmpty()) {
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if (!pools.get(EXISTING).isEmpty()) {
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
            if (null != ports && !ports.get(NEW).isEmpty()) {
                allPorts.addAll(ports.get(NEW));
                _dbClient.createObject(ports.get(NEW));
            }

            if (null != ports && !ports.get(EXISTING).isEmpty()) {
                allPorts.addAll(ports.get(EXISTING));
                _dbClient.persistObject(ports.get(EXISTING));
            }
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                    _dbClient, storageSystemId);
            List<StoragePort> allExistPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            allExistPorts.addAll(notVisiblePorts);
            _completer.statusPending(_dbClient, "Completed port discovery");

            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW),
                    allExistPorts, _dbClient, _coordinator, poolsToMatchWithVpool);
            // discover the access zone and it's network interfaces
            discoverAccessZones(storageSystem);

            // Update the virtual nas association with virtual arrays!!!
            // For existing virtual nas ports!!
            StoragePortAssociationHelper.runUpdateVirtualNasAssociationsProcess(allExistPorts, null, _dbClient);
            _completer.statusPending(_dbClient, "Completed Access Zone discovery");

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

    /**
     * discover the network interface of given Isilon storage cluster
     * 
     * @param storageSystem
     * @return
     * @throws IsilonCollectionException
     */
    private List<IsilonNetworkPool> discoverNetworkPools(StorageSystem storageSystem) throws IsilonCollectionException {
        List<IsilonNetworkPool> isilonNetworkPoolList = new ArrayList<IsilonNetworkPool>();
        URI storageSystemId = storageSystem.getId();
        _log.info("discoverNetworkPools for storage system {} - start", storageSystemId);
        List<IsilonNetworkPool> isilonNetworkPoolsTemp = null;
        try {
            if (VersionChecker.verifyVersionDetails(ONEFS_V8, storageSystem.getFirmwareVersion()) >= 0) {
                _log.info("Isilon release version {} and storagesystem label {}",
                        storageSystem.getFirmwareVersion(), storageSystem.getLabel());
                IsilonApi isilonApi = getIsilonDevice(storageSystem);
                isilonNetworkPoolsTemp = isilonApi.getNetworkPools(null);
                if (isilonNetworkPoolsTemp != null) {
                    isilonNetworkPoolList.addAll(isilonNetworkPoolsTemp);
                }
            } else {
                IsilonSshApi sshDmApi = new IsilonSshApi();
                sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(),
                        storageSystem.getPassword());
                Map<String, List<String>> networkPools = sshDmApi.getNetworkPools();
                List<String> smartconnects = null;
                IsilonNetworkPool isiNetworkPool = null;
                for (Map.Entry<String, List<String>> networkpool : networkPools.entrySet()) {
                    smartconnects = networkpool.getValue();
                    if (smartconnects != null) {

                        for (String smartconnect : smartconnects) {
                            isiNetworkPool = new IsilonNetworkPool();
                            isiNetworkPool.setAccess_zone(networkpool.getKey());
                            isiNetworkPool.setSc_dns_zone(smartconnect);
                            isilonNetworkPoolList.add(isiNetworkPool);
                        }
                    }
                }
            }
        } catch (Exception e) {
            _log.error("discover of NetworkPools is failed. %s", e.getMessage());
        }
        return isilonNetworkPoolList;
    }

    /**
     * discover the access zone and add to vipr db
     * 
     * @param storageSystem
     */
    private void discoverAccessZones(StorageSystem storageSystem) {
        URI storageSystemId = storageSystem.getId();

        VirtualNAS virtualNAS = null;
        PhysicalNAS physicalNAS = null;
        List<IsilonAccessZone> accessZoneListTemp = null;

        List<VirtualNAS> newvNASList = new ArrayList<VirtualNAS>();
        List<VirtualNAS> existingvNASList = new ArrayList<VirtualNAS>();

        List<PhysicalNAS> newPhysicalNASList = new ArrayList<PhysicalNAS>();
        List<PhysicalNAS> existingPhysicalNASList = new ArrayList<PhysicalNAS>();
        
        List<VirtualNAS> discoveredVNASList = new ArrayList<VirtualNAS>();

        // Discover storage ports
        try {
            _log.info("discoverAccessZones for storage system {} - start", storageSystemId);

            List<IsilonAccessZone> accessZoneList = new ArrayList<IsilonAccessZone>();
            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            // make restapi call to get access zones
            accessZoneListTemp = isilonApi.getAccessZones(null);
            if (accessZoneListTemp == null || accessZoneListTemp.isEmpty()) {
                // No ports defined throw an exception and fail the discovery
                IsilonCollectionException ice = new IsilonCollectionException("discoverAccessZones failed. No Zones defined");
                throw ice;
            } else {
                accessZoneList.addAll(accessZoneListTemp);
            }

            // find the smart connet zones for system
            IsilonNetworkPool isilonNetworkPoolSystem = null;
            List<IsilonNetworkPool> isilonNetworkPoolsSysAZ = new ArrayList<>();

            // get the system access zone and use it later
            List<IsilonNetworkPool> isilonNetworkPoolList = discoverNetworkPools(storageSystem);
            for (IsilonNetworkPool isilonNetworkPool : isilonNetworkPoolList) {
                if (isilonNetworkPool.getAccess_zone().equalsIgnoreCase(SYSTEM_ACCESS_ZONE_NAME)) {
                    isilonNetworkPoolsSysAZ.add(isilonNetworkPool);
                }
            }
            // set the protocol based storagesystem version
            // by default all version support CIFS and version above 7.2 NFS also
            StringSet protocols = new StringSet();
            protocols.add(CIFS);
            boolean isNfsV4Enabled = isilonApi.nfsv4Enabled(storageSystem.getFirmwareVersion());
            if (VersionChecker.verifyVersionDetails(ONEFS_V7_2, storageSystem.getFirmwareVersion()) >= 0) {
                protocols.add(NFS);
                if (isNfsV4Enabled) {
                    protocols.add(NFSv4);
                }
            }

            StoragePort storagePort = null;
            StringSet storagePorts = null;
            CifsServerMap cifsServersMap = null;
            List<IsilonNetworkPool> isilonNetworkPools = null;

            // process the access zones list
            for (IsilonAccessZone isilonAccessZone : accessZoneList) {
                // add protocol to NAS servers
                // is it a System access zone?
                isilonNetworkPools = null;

                if (isilonAccessZone.isSystem() == false) {
                    _log.info("Process the user defined access zone {} ", isilonAccessZone.toString());
                    isilonNetworkPools = new ArrayList<IsilonNetworkPool>();
                    // get the smart connect zone information
                    for (IsilonNetworkPool eachNetworkPool : isilonNetworkPoolList) {
                        if (eachNetworkPool.getAccess_zone().equalsIgnoreCase(isilonAccessZone.getName())) {
                            isilonNetworkPools.add(eachNetworkPool);
                        }
                    }
                    // if the smart connect is null then set default access zone
                    if (isilonNetworkPools != null && isilonNetworkPools.isEmpty()) {
                        isilonNetworkPools.addAll(isilonNetworkPoolsSysAZ);
                    }

                    // find virtualNAS in db
                    virtualNAS = findvNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    if (virtualNAS == null) {
                        virtualNAS = createVirtualNas(storageSystem, isilonAccessZone);
                        newvNASList.add(virtualNAS);
                    } else {
                        setMaxDbMetricsAz(storageSystem, virtualNAS.getMetrics());
                        existingvNASList.add(virtualNAS);
                        
                    }

                    // authenticate providers
                    cifsServersMap = getCifsServerMap(isilonAccessZone);
                    if (!cifsServersMap.isEmpty()) {
                        virtualNAS.setCifsServersMap(cifsServersMap);
                    }
                    // set protocol support
                    virtualNAS.setProtocols(protocols);
                    // set the smart connect
                    if (isilonNetworkPools != null && !isilonNetworkPools.isEmpty()) {
                        storagePorts = virtualNAS.getStoragePorts();
                        if (storagePorts == null) {
                            storagePorts = new StringSet();
                        } else {
                            storagePorts.clear();
                        }
                        for (IsilonNetworkPool isiNetworkPool : isilonNetworkPools) {
                            storagePort = findStoragePortByNativeId(storageSystem,
                                    isiNetworkPool.getSc_dns_zone());
                            if (storagePort != null) {
                                storagePorts.add(storagePort.getId().toString());
                            }
                        }
                        virtualNAS.setStoragePorts(storagePorts);
                    }
                } else {
                    _log.info("Process the System access zone {} ", isilonAccessZone.toString());
                    // set protocols
                    StringSet protocolSet = new StringSet();
                    protocolSet.add(CIFS);
                    protocolSet.add(NFS);
                    if (isNfsV4Enabled) {
                        protocolSet.add(NFSv4);
                    }

                    physicalNAS = findPhysicalNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    if (physicalNAS == null) {
                        physicalNAS = createPhysicalNas(storageSystem, isilonAccessZone);
                        physicalNAS.setProtocols(protocolSet);
                        // add system access zone
                        newPhysicalNASList.add(physicalNAS);
                    } else {
                        setMaxDbMetricsAz(storageSystem, physicalNAS.getMetrics());
                        existingPhysicalNASList.add(physicalNAS);
                    }
                    // add authentication providers
                    cifsServersMap = getCifsServerMap(isilonAccessZone);
                    if (!cifsServersMap.isEmpty()) {
                        physicalNAS.setCifsServersMap(cifsServersMap);
                    }

                    // set the smart connect
                    if (isilonNetworkPoolsSysAZ != null) {
                        storagePorts = physicalNAS.getStoragePorts();
                        if (storagePorts == null) {
                            storagePorts = new StringSet();
                        } else {
                            storagePorts.clear();
                        }
                        for (IsilonNetworkPool isiNetworkPool : isilonNetworkPoolsSysAZ) {
                            storagePort = findStoragePortByNativeId(storageSystem,
                                    isiNetworkPool.getSc_dns_zone());
                            if (storagePort != null) {
                                storagePorts.add(storagePort.getId().toString());
                            }
                        }
                        physicalNAS.setStoragePorts(storagePorts);
                    }
                }
            }

            // Persist the vNAS servers and
            if (newvNASList != null && !newvNASList.isEmpty()) {
                // add the parent system access zone to user defined access zones
                if (physicalNAS != null) {
                    for (VirtualNAS vNas : newvNASList) {
                        // set the parent uri or system access zone uri to vNAS
                        vNas.setParentNasUri(physicalNAS.getId());
                    }
                }
                _log.info("New Virtual NAS servers size {}", newvNASList.size());
                _dbClient.createObject(newvNASList);
                discoveredVNASList.addAll(newvNASList);
            }

            if (existingvNASList != null && !existingvNASList.isEmpty()) {
                _log.info("Modified Virtaul NAS servers size {}", existingvNASList.size());
                _dbClient.updateObject(existingvNASList);
                discoveredVNASList.addAll(existingvNASList);
            }
            // Persist the NAS servers!!!
            if (existingPhysicalNASList != null && !existingPhysicalNASList.isEmpty()) {
                _log.info("Modified Physical NAS servers size {}", existingPhysicalNASList.size());
                _dbClient.updateObject(existingPhysicalNASList);
            }

            if (newPhysicalNASList != null && !newPhysicalNASList.isEmpty()) {
                _log.info("New Physical NAS servers size {}", newPhysicalNASList.size());
                _dbClient.createObject(newPhysicalNASList);
            }
            
            DiscoveryUtils.checkVirtualNasNotVisible(discoveredVNASList, _dbClient, storageSystemId);

        } catch (Exception e) {
            _log.error("discoverAccessZones failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAccessZones failed. Storage system: " + storageSystemId);
            throw ice;
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
            _log.info("Verifying version details : Minimum Supported Version {} - Discovered Cluster Version {}", minimumSupportedVersion,
                    clusterReleaseVersion);
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, clusterReleaseVersion) < 0)
            {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                IsilonCollectionException ice = new IsilonCollectionException(String.format(
                        " ** This version of Isilon firmware is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw ice;
            }
            storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            storageSystem.setReachableStatus(true);

            _log.info("discoverCluster information for storage system {} - complete", storageSystemId);
        } catch (Exception e) {
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
            boolean isNfsV4Enabled = isilonApi.nfsv4Enabled(storageSystem.getFirmwareVersion());

            List<IsilonStoragePool> isilonStoragePools = isilonApi.getStoragePools();
            for (IsilonStoragePool isilonPool : isilonStoragePools) {
                // Check if this storage pool was already discovered
                storagePool = null;
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, isilonPool.getNativeId(),
                        NativeGUIDGenerator.POOL);
                @SuppressWarnings("deprecation")
                List<URI> poolURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getStoragePoolByNativeGuidConstraint(poolNativeGuid));

                for (URI poolUri : poolURIs) {
                    StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
                    if (!pool.getInactive() && pool.getStorageDevice().equals(storageSystemId)) {
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

                if (isNfsV4Enabled) {
                    storagePool.getProtocols().add(NFSv4);
                } else {
                    storagePool.getProtocols().remove(NFSv4);
                }

                // scale capacity size
                storagePool.setFreeCapacity(isilonPool.getAvailable() / BYTESCONVERTER);
                storagePool.setTotalCapacity(isilonPool.getTotal() / BYTESCONVERTER);
                storagePool.setSubscribedCapacity(isilonPool.getUsed() / BYTESCONVERTER);
                if (ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getCompatibilityStatus(),
                        DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())) {
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
        } catch (Exception e) {
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
                if (connInfo == null || (connInfo != null && connInfo.getSmartZones() == null)) {
                    throw new Exception("Failed new Interface, try old Interface");
                }
                if (connInfo != null) {
                    isilonStoragePorts = connInfo.getPorts();
                }
            } catch (Exception e) {
                _log.info("Latest version failed so Trying to get old smart connect version");
                IsilonSmartConnectInfo connInfo = isilonApi.getSmartConnectInfo();
                if (connInfo != null) {
                    isilonStoragePorts = connInfo.getPorts();
                }
            }

            if (isilonStoragePorts == null || isilonStoragePorts.isEmpty()) {
                // No ports defined throw an exception and fail the discovery
                IsilonCollectionException ice = new IsilonCollectionException("discoverPorts failed. No Smartzones defined");
                throw ice;
            }

            for (IsilonStoragePort isilonPort : isilonStoragePorts) {

                StoragePort storagePort = null;

                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, isilonPort.getIpAddress(),
                        NativeGUIDGenerator.PORT);
                // Check if storage port was already discovered
                @SuppressWarnings("deprecation")
                List<URI> portURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getStoragePortByNativeGuidConstraint(portNativeGuid));
                for (URI portUri : portURIs) {
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

    /**
     * add user define the access zone to Discovery path
     * 
     * @param accessZones
     */
    void setDiscPathForAccess(List<IsilonAccessZone> accessZones) {
        if (accessZones != null && !accessZones.isEmpty()) {
            for (IsilonAccessZone isilonAccessZone : accessZones) {
                if (isilonAccessZone.isSystem() == false) {
                    getDiscPathsForUnManaged().add(isilonAccessZone.getPath() + "/");
                    _log.info("setDiscPathForAccess: {}", isilonAccessZone.getPath() + "/");
                }
            }
        }
    }

    /**
     * get the NAS Server object
     * 
     * @param nasServerMap
     * @param fsPath
     * @return
     */
    private NASServer getMatchedNASServer(Map<String, NASServer> nasServerMap, String fsPath) {
        NASServer nasServer = null;
        if (nasServerMap != null && !nasServerMap.isEmpty()) {
            for (Entry<String, NASServer> entry : nasServerMap.entrySet()) {
                if (!SYSTEM_ACCESS_ZONE_NAME.equals(entry.getValue().getNasName())) {
                    if (fsPath.startsWith(entry.getKey())) {
                        nasServer = entry.getValue();
                        break;
                    }
                }
            }
            if (nasServer == null) {
                nasServer = nasServerMap.get(IFS_ROOT + "/");
            }
        }

        return nasServer;
    }

    private void discoverUmanagedFileSystems(AccessProfile profile) throws BaseCollectionException {

        _log.debug("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                profile.getIpAddress() + profile.getPortNumber(),
                profile.getnamespace());

        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();

        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();

        String detailedStatusMessage = "Discovery of Isilon Unmanaged FileSystem started";
        long unmanagedFsCount = 0;
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
                if (storagePool != null && !storagePool.getInactive()) {
                    pools.add(storagePool);
                }
            }

            StoragePool storagePool = null;
            if (pools != null && !pools.isEmpty()) {
                storagePool = pools.get(0);
            }

            StoragePort storagePort = getStoragePortPool(storageSystem);

            String resumeToken = null;

            int totalIsilonFSDiscovered = 0;

            // get the associated storage port for vnas Server
            List<IsilonAccessZone> isilonAccessZones = isilonApi.getAccessZones(null);
            setDiscPathForAccess(isilonAccessZones);
            Map<String, NASServer> nasServers = getNASServer(storageSystem, isilonAccessZones);

            // Get All FileShare
            HashMap<String, HashSet<String>> allSMBShares = discoverAllSMBShares(storageSystem, isilonAccessZones);
            List<UnManagedCifsShareACL> unManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            List<UnManagedCifsShareACL> oldunManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();

            HashMap<String, HashSet<Integer>> expMap = discoverAllExports(storageSystem, isilonAccessZones);

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

                for (FileShare fs : discoveredFS) {
                    if (!checkStorageFileSystemExistsInDB(fs.getNativeGuid())) {
                        // Create UnManaged FS
                        String fsUnManagedFsNativeGuid =
                                NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                        storageSystem.getSerialNumber(), fs.getNativeId());
                        String fsPathName = fs.getPath();
                        UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                        // get the matched vNAS Server
                        NASServer nasServer = getMatchedNASServer(nasServers, fsPathName);
                        if (nasServer != null) {
                            _log.info("fs path {} and nas server details {}", fs.getPath(), nasServer.toString());
                            if (nasServer.getStoragePorts() != null && !nasServer.getStoragePorts().isEmpty()) {
                                storagePort = _dbClient.queryObject(StoragePort.class,
                                        URI.create(nasServer.getStoragePorts().iterator().next()));
                            }
                        } else {
                            _log.info("fs path {} and vnas server not found", fs.getPath(), nasServer.toString());
                        }

                        boolean alreadyExist = unManagedFs == null ? false : true;
                        unManagedFs = createUnManagedFileSystem(unManagedFs,
                                fsUnManagedFsNativeGuid, storageSystem, storagePool, nasServer, fs);

                        // get umcifs & ACLs for given filesystem
                        UnManagedCifsShareACL existingACL = null;
                        List<UnManagedCifsShareACL> tempunManagedCifsShareACL = new ArrayList<UnManagedCifsShareACL>();
                        int noOfShares = 0;

                        // get all shares for given file system path
                        HashSet<String> smbShareHashSet = new HashSet<String>();
                        for (Entry<String, HashSet<String>> entry : allSMBShares.entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(fsPathName) || entry.getKey().startsWith(fsPathName + "/")) {
                                _log.info("filesystem path : {} and share path: {}", fs.getPath(), entry.getKey());
                                smbShareHashSet.addAll(entry.getValue());
                                noOfShares += 1;
                            }
                        }

                        if (!smbShareHashSet.isEmpty()) {
                            // get UnManaged ACL and also set the shares in fs object
                            getUnmanagedCifsShareACL(unManagedFs, smbShareHashSet,
                                    tempunManagedCifsShareACL, storagePort, fs.getName(), nasServer.getNasName(), isilonApi);
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
                                    // set native guid, so each entry unique
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
                                _log.info("File System {} has ACL and their size is {}", unManagedFs.getId(),
                                        tempunManagedCifsShareACL.size());
                            }
                        }

                        // Get Export info
                        _log.info("Getting export for {}", fs.getPath());
                        HashMap<String, HashSet<Integer>> expIdMap = exportMapTree.get(fs.getPath());

                        if (expIdMap == null) {
                            expIdMap = new HashMap<>();
                        }

                        List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();
                        if (!expIdMap.keySet().isEmpty()) {
                            boolean validExportsFound = getUnManagedFSExportMap(unManagedFs, expIdMap, storagePort,
                                    fs.getPath(), nasServer.getNasName(), isilonApi);
                            if (!validExportsFound) {
                                // Invalid exports so ignore the FS
                                String invalidExports = "";
                                for (String path : expIdMap.keySet()) {
                                    invalidExports += expIdMap.get(path);
                                }
                                _log.info("FS {} is ignored because it has conflicting exports {}", fs.getPath(), invalidExports);
                                unManagedFs.setInactive(true);
                                // Persists the inactive state before picking next UMFS!!!
                                _dbClient.persistObject(unManagedFs);
                                continue;
                            }
                            List<UnManagedFileExportRule> validExportRules = getUnManagedFSExportRules(unManagedFs, expIdMap, storagePort,
                                    fs.getPath(), nasServer.getNasName(), isilonApi);
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
                                if (null == existingRule) {
                                    unManagedExportRules.add(dbExportRule);
                                } else {
                                    existingRule.setInactive(true);
                                    _dbClient.persistObject(existingRule);
                                    unManagedExportRules.add(dbExportRule);
                                }
                            }

                            // Validate Rules Compatible with ViPR - Same rules should
                            // apply as per API SVC Validations.
                            if (!unManagedExportRules.isEmpty()) {
                                _log.info("Validating rules success for export {}", fs.getName());
                                newUnManagedExportRules.addAll(unManagedExportRules);
                                unManagedFs.setHasExports(true);
                                _log.info("File System {} has Exports and their size is {}", unManagedFs.getId(),
                                        newUnManagedExportRules.size());
                            }
                        }

                        if (unManagedFs.getHasExports() || unManagedFs.getHasShares()) {
                            _log.info("FS {} is having exports/shares", fs.getPath());
                            unManagedFs.putFileSystemCharacterstics(
                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString(), TRUE);
                        } else {
                            // NO exports found
                            _log.info("FS {} is ignored because it doesnt have exports and shares", fs.getPath());
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
                        // save ACLs in db
                        if (!unManagedCifsShareACLList.isEmpty() && unManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                            _log.info("Saving Number of UnManagedCifsShareACL(s) {}", unManagedCifsShareACLList.size());
                            _dbClient.createObject(unManagedCifsShareACLList);
                            unManagedCifsShareACLList.clear();
                        }
                        // save old acls
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
                if (!unManagedFileSystems.isEmpty()) {
                    _dbClient.createObject(unManagedFileSystems);
                }
                if (!existingUnManagedFileSystems.isEmpty()) {
                    _dbClient.updateAndReindexObject(existingUnManagedFileSystems);
                }

            } while (resumeToken != null);

            // save ACLs in db
            if (!unManagedCifsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedCifsShareACL(s) {}", unManagedCifsShareACLList.size());
                _dbClient.createObject(unManagedCifsShareACLList);
                unManagedCifsShareACLList.clear();
            }
            // save old acls
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
     * 
     * @param storageSystem
     * @return
     */

    private HashMap<String, HashSet<String>> discoverAllSMBShares(final StorageSystem storageSystem,
            final List<IsilonAccessZone> isilonAccessZones) {
        // Discover All FileShares
        String resumeToken = null;
        HashMap<String, HashSet<String>> allShares = new HashMap<String, HashSet<String>>();
        URI storageSystemId = storageSystem.getId();
        _log.info("discoverAllShares for storage system {} - start", storageSystemId);

        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            for (IsilonAccessZone isilonAccessZone : isilonAccessZones) {
                do {
                    IsilonApi.IsilonList<IsilonSMBShare> isilonShares =
                            isilonApi.listShares(resumeToken, isilonAccessZone.getName());

                    List<IsilonSMBShare> isilonSMBShareList = isilonShares.getList();
                    HashSet<String> sharesHashSet = null;
                    for (IsilonSMBShare share : isilonSMBShareList) {
                        // get the filesystem path and shareid
                        String path = share.getPath();
                        String shareId = share.getId();
                        sharesHashSet = allShares.get(path);
                        if (null == sharesHashSet) {
                            sharesHashSet = new HashSet<String>();
                            sharesHashSet.add(shareId);
                            allShares.put(path, sharesHashSet);
                        } else {
                            // if shares already exist for path then add
                            sharesHashSet.add(shareId);
                            allShares.put(path, sharesHashSet);
                        }

                        _log.info("Discovered SMB Share name {} and path {}", shareId, path);
                    }

                    resumeToken = isilonShares.getToken();
                } while (resumeToken != null);
                _log.info("discoverd AllShares for access zone {} ", isilonAccessZone.getName());
                resumeToken = null;
            }

            return allShares;
        } catch (IsilonException ie) {
            _log.error("discoverAllShares failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllShares failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("discoverAllShares failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllShares failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private void validateListSizeLimitAndPersist(List<UnManagedFileSystem> newUnManagedFileSystems,
            List<UnManagedFileSystem> existingUnManagedFileSystems, int limit) {

        if (newUnManagedFileSystems != null && !newUnManagedFileSystems.isEmpty() &&
                newUnManagedFileSystems.size() >= limit) {
            _partitionManager.insertInBatches(newUnManagedFileSystems,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                    UNMANAGED_FILESYSTEM);
            newUnManagedFileSystems.clear();
        }

        if (existingUnManagedFileSystems != null && !existingUnManagedFileSystems.isEmpty() &&
                existingUnManagedFileSystems.size() >= limit) {
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

            HashMap<String, FileShare> fsWithQuotaMap = new HashMap<String, FileShare>();
            // get first page of quota data, process and insert to database

            IsilonApi.IsilonList<IsilonSmartQuota> quotas = isilonApi.listQuotas(null);
            boolean qualified = false;
            for (IsilonSmartQuota quota : quotas.getList()) {
                String fsNativeId = quota.getPath();

                qualified = isUnderUnmanagedDiscoveryPath(fsNativeId);
                if (qualified) {
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
                    if (qualified) {
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

            // Filter out FS with no Quota associated with them
            discoveredFS = new ArrayList<FileShare>(fsWithQuotaMap.values());
            IsilonApi.IsilonList<FileShare> isilonFSList = new IsilonApi.IsilonList<FileShare>();
            isilonFSList.addList(discoveredFS);
            // isilonFSList.setToken(isilonFileSystems.getToken());
            return isilonFSList;

        } catch (IsilonException ie) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllFileSystem failed. Storage system: "
                    + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllFileSystem failed. Storage system: "
                    + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private FileShare extractFileShare(String fsNativeId, IsilonSmartQuota quota, StorageSystem storageSystem) {

        _log.debug("extractFileShare for {} and quota {} ", fsNativeId, quota.toString());
        FileShare fs = new FileShare();

        String[] splits = fsNativeId.split("/");
        if (splits.length > 0) {
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
        if (!quota.getId().equalsIgnoreCase("null")) {
            fs.getExtensions().put(QUOTA, quota.getId());
        }
        return fs;
    }

    private boolean isUnderUnmanagedDiscoveryPath(String fsNativeId) {
        boolean qualified = false;

        for (String discPath : _discPathsForUnManaged) {
            if (fsNativeId.startsWith(discPath)) {
                qualified = true;
                break;
            }
        }
        return qualified;
    }

    private HashMap<String, HashSet<Integer>>
            discoverAllExports(StorageSystem storageSystem, final List<IsilonAccessZone> isilonAccessZones)
                    throws IsilonCollectionException {

        // Discover All FileSystem
        HashMap<String, HashSet<Integer>> allExports = new HashMap<String, HashSet<Integer>>();

        URI storageSystemId = storageSystem.getId();

        String resumeToken = null;

        try {
            _log.info("discoverAllExports for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            for (IsilonAccessZone isilonAccessZone : isilonAccessZones) {
                do {
                    IsilonApi.IsilonList<IsilonExport> isilonExports = isilonApi.listExports(resumeToken,
                            isilonAccessZone.getName());
                    List<IsilonExport> exports = isilonExports.getList();

                    for (IsilonExport exp : exports) {
                        _log.info("Discovered fS export {}", exp.toString());
                        HashSet<Integer> exportIds = new HashSet<Integer>();
                        for (String path : exp.getPaths()) {
                            exportIds = allExports.get(path);
                            if (exportIds == null) {
                                exportIds = new HashSet<Integer>();
                            }
                            exportIds.add(exp.getId());
                            allExports.put(path, exportIds);
                            _log.debug("Discovered fS put export Path {} Export id {}", path, exportIds.size() + ":" + exportIds);
                        }
                    }
                    resumeToken = isilonExports.getToken();
                } while (resumeToken != null);
                _log.info("discoverd All NFS Exports for access zone {} ", isilonAccessZone.getName());
                resumeToken = null;
            }

            return allExports;

        } catch (IsilonException ie) {
            _log.error("discoverAllExports failed. Storage system: {}", storageSystemId, ie);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllExports failed. Storage system: " + storageSystemId);
            ice.initCause(ie);
            throw ice;
        } catch (Exception e) {
            _log.error("discoverAllExports failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllExports failed. Storage system: " + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private IsilonExport getIsilonExport(IsilonApi isilonApi, Integer expId, String zoneName) {
        IsilonExport exp = null;
        try {
            _log.debug("call getIsilonExport for {} ", expId);
            if (expId != null) {
                exp = isilonApi.getExport(expId.toString(), zoneName);
                _log.debug("call getIsilonExport {}", exp.toString());
            }
        } catch (Exception e) {
            _log.error("Exception while getting Export for {}", expId);
        }
        return exp;
    }

    /**
     * get UnManaged Cifs Shares and their ACLs
     * 
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
            String zoneName,
            IsilonApi isilonApi) {
        _log.info("getUnmanagedCifsShareACL for UnManagedFileSystem file path: {} - start", fsname);

        // HashSet<String> smbShares = allShares.get(fsPath);
        if (null != smbShares && !smbShares.isEmpty()) {
            UnManagedSMBShareMap unManagedSmbShareMap = null;
            if (null == unManagedFileSystem.getUnManagedSmbShareMap()) {
                unManagedSmbShareMap = new UnManagedSMBShareMap(); // initialise
                unManagedFileSystem.setUnManagedSmbShareMap(unManagedSmbShareMap);
            }
            unManagedSmbShareMap = unManagedFileSystem.getUnManagedSmbShareMap();
            UnManagedSMBFileShare unManagedSMBFileShare = null;

            for (String shareId : smbShares) {
                // get smb share details
                IsilonSMBShare isilonSMBShare = getIsilonSMBShare(isilonApi, shareId, zoneName);
                if (null != isilonSMBShare) {
                    unManagedSMBFileShare = new UnManagedSMBFileShare();
                    unManagedSMBFileShare.setName(isilonSMBShare.getName());
                    unManagedSMBFileShare.setDescription(isilonSMBShare.getDescription());
                    unManagedSMBFileShare.setNativeId(unManagedFileSystem.getNativeGuid());
                    unManagedSMBFileShare.setMountPoint("\\\\" + storagePort.getPortNetworkId() + "\\" + isilonSMBShare.getName());
                    unManagedSMBFileShare.setPath(isilonSMBShare.getPath());
                    unManagedSMBFileShare.setMaxUsers(-1);
                    // setting the dummy permission.This is not used by isilon, but used by other storage system
                    unManagedSMBFileShare.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);
                    unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);

                    // set Unmanaged SMB Share
                    unManagedSmbShareMap.put(isilonSMBShare.getName(), unManagedSMBFileShare);
                    _log.info("smb share name {} and fs mount point {} ", unManagedSMBFileShare.getName(),
                            unManagedSMBFileShare.getMountPoint());
                    // process ACL permission
                    UnManagedCifsShareACL unManagedCifsShareACL = null;
                    List<IsilonSMBShare.Permission> permissionList = isilonSMBShare.getPermissions();
                    for (IsilonSMBShare.Permission permission : permissionList) {
                        // Isilon can have deny permission type. Do not ingest the ACL for deny

                        if (FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW
                                .equalsIgnoreCase(permission.getPermissionType())) {

                            unManagedCifsShareACL = new UnManagedCifsShareACL();
                            // share name
                            unManagedCifsShareACL.setShareName(isilonSMBShare.getName());
                            // permission
                            unManagedCifsShareACL.setPermission(permission.getPermission());

                            // we take only username and we can ignore type and id
                            // user
                            unManagedCifsShareACL.setUser(permission.getTrustee().getName());

                            // filesystem id
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
        return;
    }

    @Override
    public void scan(AccessProfile arg0) throws BaseCollectionException {
        // TODO Auto-generated method stub
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
            StoragePool pool, NASServer nasServer, FileShare fileSystem)
            throws IOException, IsilonCollectionException {

        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(storageSystem.getId());
            if (null != pool) {
                unManagedFileSystem.setStoragePoolUri(pool.getId());
            }
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
        }

        if (null == unManagedFileSystem.getExtensions()) {
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
                        .toString(), FALSE);

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(), pools);
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId(),
                    unManagedFileSystemCharacteristics
                            .get(UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                                    .toString()));
            _log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpools.
                unManagedFileSystem.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedFileSystem.getSupportedVpoolUris().replace(matchedVPools);
                _log.info("Replaced Pools :"
                        + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
            }

        }

        if (null != nasServer) {
            StringSet storagePorts = new StringSet();
            if (nasServer.getStoragePorts() != null && !nasServer.getStoragePorts().isEmpty()) {
                storagePorts.addAll(nasServer.getStoragePorts());
                unManagedFileSystemInformation.put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
                _log.info("StoragePorts :"
                        + Joiner.on("\t").join(storagePorts));
            }

            StringSet nasServerSet = new StringSet();
            nasServerSet.add(nasServer.getId().toString());
            unManagedFileSystemInformation.put(UnManagedFileSystem.SupportedFileSystemInformation.NAS.toString(), nasServerSet);
            _log.debug("nasServer uri id {}", nasServer.getId().toString());
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

        // Set attributes of FileSystem
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
        if (fileSystem.getCapacity() != null) {
            capacity = fileSystem.getCapacity();
        }
        provisionedCapacity.add(String.valueOf(capacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(), provisionedCapacity);

        StringSet allocatedCapacity = new StringSet();
        long usedCapacity = 0;
        if (fileSystem.getUsedCapacity() != null) {
            usedCapacity = fileSystem.getUsedCapacity();
        }
        allocatedCapacity.add(String.valueOf(usedCapacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(), allocatedCapacity);

        String quotaId = fileSystem.getExtensions().get(QUOTA);
        if (quotaId != null) {
            unManagedFileSystem.getExtensions().put(QUOTA, quotaId);
        }
        _log.debug("Quota : {}  : {}", quotaId, fileSystem.getPath());

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .addFileSystemCharacterstcis(unManagedFileSystemCharacteristics);

        // Initialize ExportMap
        unManagedFileSystem.setFsUnManagedExportMap(new UnManagedFSExportMap());

        // Initialize SMBMap
        unManagedFileSystem.setUnManagedSmbShareMap(new UnManagedSMBShareMap());

        return unManagedFileSystem;
    }

    /**
     * get share details
     * 
     * @param isilonApi
     * @param shareId
     * @return
     */
    private IsilonSMBShare getIsilonSMBShare(IsilonApi isilonApi, String shareId, String zoneName) {
        _log.debug("call getIsilonSMBShare for {} ", shareId);
        IsilonSMBShare isilonSMBShare = null;
        try {
            if (isilonApi != null) {
                isilonSMBShare = isilonApi.getShare(shareId, zoneName);
                _log.debug("call getIsilonSMBShare {}", isilonSMBShare.toString());
            }
        } catch (Exception e) {
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
            if (fileShare != null && !fileShare.getInactive()) {
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
        for (URI poolURI : poolURIs) {
            pool = _dbClient.queryObject(StoragePool.class, poolURI);
            if (pool != null && !pool.getInactive()) {
                return pool;
            }
        }
        return null;
    }

    /*
     * get Storage Pool
     * 
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
        while (storagePoolIter.hasNext()) {
            URI storagePoolURI = storagePoolIter.next();
            storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
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
        while (storagePortIter.hasNext()) {
            URI storagePortURI = storagePortIter.next();
            storagePort = _dbClient.queryObject(StoragePort.class,
                    storagePortURI);
            if (storagePort != null && !storagePort.getInactive()) {
                _log.debug("found a port for storage system  {} {}",
                        storageSystem.getSerialNumber(), storagePort);
                return storagePort;
            }
        }
        return null;
    }

    /**
     * get the NAS server list
     * 
     * @param storageSystem
     * @param accessZones
     * @return
     */
    private Map<String, NASServer> getNASServer(final StorageSystem storageSystem,
            List<IsilonAccessZone> accessZones) {
        NASServer nasServer = null;
        Map<String, NASServer> accessZonesMap = new HashMap<String, NASServer>();
        if (accessZones != null && !accessZones.isEmpty()) {
            for (IsilonAccessZone isilonAccessZone : accessZones) {
                if (isilonAccessZone.isSystem() == false) {
                    nasServer = findvNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());

                    accessZonesMap.put(isilonAccessZone.getPath() + "/", nasServer);
                } else {
                    nasServer = findPhysicalNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    accessZonesMap.put(isilonAccessZone.getPath() + "/", nasServer);
                }
            }
        }
        return accessZonesMap;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     * Ignore exports with multiple exports for the same path
     * Ignore exports that have multiple security flavors
     * Ignore exports with multiple paths
     * Ignore exports not found on the array
     * Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     * 
     * @param umfs
     * @param expIdMap
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private List<UnManagedFileExportRule> getUnManagedFSExportRules(UnManagedFileSystem umfs, HashMap<String, HashSet<Integer>> expIdMap,
            StoragePort storagePort, String fsPath, String zoneName, IsilonApi isilonApi) {
        List<UnManagedFileExportRule> exportRules = new ArrayList<UnManagedFileExportRule>();
        UnManagedExportVerificationUtility validationUtility =
                new UnManagedExportVerificationUtility(_dbClient);
        List<UnManagedFileExportRule> exportRulesTemp = null;
        boolean isAllRulesValid = true;
        for (String expMapPath : expIdMap.keySet()) {
            HashSet<Integer> isilonExportIds = new HashSet<>();
            _log.info("getUnManagedFSExportMap {} : export ids : {}",
                    expMapPath, expIdMap.get(expMapPath));
            isilonExportIds = expIdMap.get(expMapPath);
            if (isilonExportIds != null && !isilonExportIds.isEmpty()) {
                exportRulesTemp = getUnManagedFSExportRules(umfs, storagePort,
                        isilonExportIds, expMapPath, zoneName, isilonApi);
                // validate export rules for each path
                if (null != exportRulesTemp && !exportRulesTemp.isEmpty()) {
                    isAllRulesValid = validationUtility.validateUnManagedExportRules(
                            exportRulesTemp, false);
                    if (isAllRulesValid) {
                        _log.info("Validating rules success for export {}", expMapPath);
                        exportRules.addAll(exportRulesTemp);
                    } else {
                        _log.info("Ignroing the rules for export {}", expMapPath);
                        isAllRulesValid = false;
                    }
                }
            }
        }

        if (exportRules.isEmpty() || false == isAllRulesValid) {
            umfs.setHasExports(false);
            _log.info("FileSystem " + fsPath + " does not have valid ViPR exports ");
            exportRules.clear();
        }
        return exportRules;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     * Ignore exports with multiple exports for the same path
     * Ignore exports that have multiple security flavors
     * Ignore exports with multiple paths
     * Ignore exports not found on the array
     * Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     * 
     * @param umfs
     * @param expIdMap
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private boolean getUnManagedFSExportMap(UnManagedFileSystem umfs, HashMap<String, HashSet<Integer>> expIdMap,
            StoragePort storagePort, String fsPath, String zoneName, IsilonApi isilonApi) {

        UnManagedFSExportMap exportMap = new UnManagedFSExportMap();

        boolean validExports = false;

        for (String expMapPath : expIdMap.keySet()) {
            HashSet<Integer> isilonExportIds = new HashSet<>();
            _log.info("getUnManagedFSExportMap {} : export ids : {}", expMapPath, expIdMap.get(expMapPath));
            isilonExportIds = expIdMap.get(expMapPath);
            if (isilonExportIds != null && !isilonExportIds.isEmpty()) {
                validExports = getUnManagedFSExportMap(umfs, isilonExportIds,
                        storagePort, expMapPath, zoneName, isilonApi);
            } else {
                validExports = false;
            }
            if (!validExports) {
                // perform resetting umfs export map
                umfs.setFsUnManagedExportMap(exportMap);
                return false;
            }
        }
        return true;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     * Ignore exports with multiple exports for the same path
     * Ignore exports that have multiple security flavors
     * Ignore exports with multiple paths
     * Ignore exports not found on the array
     * Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     * 
     * @param umfs
     * @param isilonExportIds
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private boolean getUnManagedFSExportMap(UnManagedFileSystem umfs, HashSet<Integer> isilonExportIds,
            StoragePort storagePort, String fsPath, String zoneName, IsilonApi isilonApi) {

        UnManagedFSExportMap exportMap = new UnManagedFSExportMap();

        int generatedExportCount = 0;

        ArrayList<IsilonExport> isilonExports = new ArrayList<IsilonExport>();

        if (isilonExportIds != null && isilonExportIds.size() > 1) {
            _log.info("Ignoring file system {}, Multiple exports found {} ", fsPath, isilonExportIds.size());
            return false;
        }

        for (Integer expId : isilonExportIds) {
            IsilonExport exp = getIsilonExport(isilonApi, expId, zoneName);
            if (exp == null) {
                _log.info("Ignoring file system {}, export {} not found", fsPath, expId);
                return false;
            } else if (exp.getSecurityFlavors().size() > 1) {
                _log.info("Ignoring file system {}, multiple security flavors {} found", fsPath, exp.getSecurityFlavors().toString());
                return false;
            } else if (exp.getPaths().size() > 1) {
                _log.info("Ignoring file system {}, multiple paths {} found", fsPath, exp.getPaths().toString());
                return false;
            } else {
                isilonExports.add(exp);
            }
        }

        for (IsilonExport exp : isilonExports) {
            String securityFlavor = exp.getSecurityFlavors().get(0);
            // Isilon Maps sys to unix and we do this conversion during export from ViPR
            if (securityFlavor.equalsIgnoreCase(UNIXSECURITY)) {
                securityFlavor = SYSSECURITY;
            }

            String path = exp.getPaths().get(0);

            // Get User
            String rootUserMapping = "";
            String mapAllUserMapping = "";
            if (exp.getMap_root() != null && exp.getMap_root().getUser() != null) {
                rootUserMapping = exp.getMap_root().getUser();
            } else if (exp.getMap_all() != null && exp.getMap_all().getUser() != null) {
                mapAllUserMapping = exp.getMap_all().getUser();
            }

            String resolvedUser = (rootUserMapping != null && (!rootUserMapping.isEmpty())) ? rootUserMapping : mapAllUserMapping;

            if (exp != null && exp.getReadOnlyClients() != null && !exp.getReadOnlyClients().isEmpty()) {
                UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                        exp.getReadOnlyClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor, RO,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedROFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                generatedExportCount++;
            }

            if (exp != null && exp.getReadWriteClients() != null && !exp.getReadWriteClients().isEmpty()) {
                UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                        exp.getReadWriteClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor, RW,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedRWFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedRWFSExport.getFileExportKey(), unManagedRWFSExport);
                generatedExportCount++;
            }

            if (exp != null && exp.getRootClients() != null && !exp.getRootClients().isEmpty()) {
                UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                        exp.getRootClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        securityFlavor, ROOT,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        path);
                unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                generatedExportCount++;
            }

            if (exp.getReadOnlyClients() != null && exp.getReadWriteClients() != null && exp.getRootClients() != null) {
                // Check Clients size
                if (exp.getReadOnlyClients().isEmpty() && exp.getReadWriteClients().isEmpty() && exp.getRootClients().isEmpty()) {
                    // All hosts case. Check whether it is RO/RW/ROOT

                    if (exp.getReadOnly()) {
                        // This is a read only export for all hosts
                        UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                                exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                securityFlavor, RO,
                                rootUserMapping, NFS, storagePort.getPortName(), path,
                                path);
                        unManagedROFSExport.setIsilonId(exp.getId().toString());
                        exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                        generatedExportCount++;
                    } else {
                        // Not read Only case
                        if (exp.getMap_all() != null && exp.getMap_all().getUser() != null
                                && exp.getMap_all().getUser().equalsIgnoreCase(ROOT)) {
                            // All hosts with root permission
                            UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    securityFlavor, ROOT,
                                    mapAllUserMapping, NFS, storagePort.getPortName(), path,
                                    path);
                            unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                            exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                            generatedExportCount++;

                        } else if (exp.getMap_all() != null) {
                            // All hosts with RW permission
                            UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    securityFlavor, RW,
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

        if (exportMap.values().size() < generatedExportCount) {
            // The keys are not unique and so all the exports are not valid
            _log.info(
                    "Ignoring Exports because they have multiple exports with the same internal export key <sec, perm, root-mapping>. Expected {} got {}",
                    generatedExportCount, exportMap.values().size());
            return false;
        }

        // Return valid
        UnManagedFSExportMap allExportMap = umfs.getFsUnManagedExportMap();
        if (allExportMap == null) {
            allExportMap = new UnManagedFSExportMap();
        }
        allExportMap.putAll(exportMap);
        umfs.setFsUnManagedExportMap(allExportMap);

        return true;
    }

    /**
     * Generate Export Map for UnManagedFileSystem
     * Ignore exports with multiple exports for the same path
     * Ignore exports that have multiple security flavors
     * Ignore exports with multiple paths
     * Ignore exports not found on the array
     * Ignore exports which have the same internal export key ( <sec, perm, root-mapping>)
     * 
     * @param umfs
     * @param isilonExportIds
     * @param storagePort
     * @param fsPath
     * @param isilonApi
     * @return boolean
     */
    private List<UnManagedFileExportRule> getUnManagedFSExportRules(UnManagedFileSystem umfs, StoragePort storagePort,
            HashSet<Integer> isilonExportIds, String fsPath, String zoneName, IsilonApi isilonApi) {

        List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();
        ArrayList<IsilonExport> isilonExports = new ArrayList<IsilonExport>();

        if (isilonExportIds != null && isilonExportIds.size() > 1) {
            _log.info("Ignoring file system {}, Multiple exports found {} ", fsPath, isilonExportIds.size());
        }

        for (Integer expId : isilonExportIds) {
            IsilonExport exp = getIsilonExport(isilonApi, expId, zoneName);
            if (exp == null) {
                _log.info("Ignoring file system {}, export {} not found", fsPath, expId);
            } else if (exp.getSecurityFlavors().size() > 1) {
                _log.info("Ignoring file system {}, multiple security flavors {} found", fsPath, exp.getSecurityFlavors().toString());
            } else if (exp.getPaths().size() > 1) {
                _log.info("Ignoring file system {}, multiple paths {} found", fsPath, exp.getPaths().toString());
            } else {
                isilonExports.add(exp);
            }
        }

        for (IsilonExport exp : isilonExports) {
            String securityFlavor = exp.getSecurityFlavors().get(0);
            // Isilon Maps sys to unix and we do this conversion during export from ViPR
            if (securityFlavor.equalsIgnoreCase(UNIXSECURITY)) {
                securityFlavor = SYSSECURITY;
            }

            String path = exp.getPaths().get(0);

            // Get User
            String rootUserMapping = "";
            String mapAllUserMapping = "";
            if (exp.getMap_root() != null && exp.getMap_root().getUser() != null) {
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

            if (exp != null && exp.getReadOnlyClients() != null && !exp.getReadOnlyClients().isEmpty()) {
                expRule.setReadOnlyHosts(new StringSet(exp.getReadOnlyClients()));
            }

            if (exp != null && exp.getReadWriteClients() != null && !exp.getReadWriteClients().isEmpty()) {
                expRule.setReadWriteHosts(new StringSet(exp.getReadWriteClients()));
            }

            if (exp != null && exp.getRootClients() != null && !exp.getRootClients().isEmpty()) {
                expRule.setRootHosts(new StringSet(exp.getRootClients()));
            }

            if (exp.getReadOnlyClients() != null && exp.getReadWriteClients() != null && exp.getRootClients() != null) {
                // Check Clients size
                if (exp.getReadOnlyClients().isEmpty() && exp.getReadWriteClients().isEmpty() && exp.getRootClients().isEmpty()) {
                    // All hosts case. Check whether it is RO/RW/ROOT

                    if (exp.getReadOnly()) {
                        // This is a read only export for all hosts
                        expRule.setReadOnlyHosts(new StringSet(exp.getClients()));
                    } else {
                        // Not read Only case
                        if (exp.getMap_all() != null && exp.getMap_all().getUser() != null
                                && exp.getMap_all().getUser().equalsIgnoreCase(ROOT)) {
                            // All hosts with root permission
                            expRule.setRootHosts(new StringSet(exp.getClients()));

                        } else if (exp.getMap_all() != null) {
                            // All hosts with RW permission
                            expRule.setReadWriteHosts(new StringSet(exp.getClients()));
                        }
                    }
                }
            }
            expRules.add(expRule);
        }

        return expRules;
    }

    private HashMap<String, HashSet<Integer>> getExportsIncludingSubDir(String fsPath, HashMap<String, HashSet<Integer>> expMap) {
        HashMap<String, HashSet<Integer>> expMapWithIds = new HashMap<>();
        for (String expPath : expMap.keySet()) {
            if (expPath.equalsIgnoreCase(fsPath) || expPath.contains(fsPath + "/")) {
                HashSet<Integer> expIds = expMap.get(expPath);
                if (expIds != null && !expIds.isEmpty()) {
                    expMapWithIds.put(expPath, expIds);
                } else {
                    expMapWithIds.put(expPath, new HashSet<Integer>());
                }
            }
        }
        return expMapWithIds;
    }

    private HashMap<String, HashMap<String, HashSet<Integer>>> getExportsWithSubDirForFS(List<FileShare> discoveredIsilonFS,
            HashMap<String, HashSet<Integer>> expMap) {
        HashMap<String, HashMap<String, HashSet<Integer>>> expMapTree = new HashMap<>();
        for (FileShare fs : discoveredIsilonFS) {
            expMapTree.put(fs.getPath(), getExportsIncludingSubDir(fs.getPath(), expMap));

        }
        return expMapTree;
    }

    /**
     * set the Max limits for static db metrics
     * 
     * @param system
     * @param dbMetrics
     */
    private void setMaxDbMetricsAz(final StorageSystem system, StringMap dbMetrics) {
        // Set the Limit Metric keys!!
        dbMetrics.put(MetricsKeys.maxStorageObjects.name(), String.valueOf(MAX_STORAGE_OBJECTS));

        Long MaxNfsExports = 0L;
        Long MaxCifsShares = 30000L;

        if (VersionChecker.verifyVersionDetails(ONEFS_V7_2, system.getFirmwareVersion()) > 0) {
            MaxNfsExports = MAX_NFS_EXPORTS_V7_2;
            MaxCifsShares = MAX_CIFS_SHARES;
        }

        dbMetrics.put(MetricsKeys.maxNFSExports.name(), String.valueOf(MaxNfsExports));
        dbMetrics.put(MetricsKeys.maxCifsShares.name(), String.valueOf(MaxCifsShares));

        // set the max capacity in GB
        long MaxCapacity = Math.round(getClusterStorageCapacity(system));
        dbMetrics.put(MetricsKeys.maxStorageCapacity.name(), String.valueOf(MaxCapacity*GB_IN_BYTES));
        return;
    }

    /**
     * get the cluster capacity using ssh cli command
     * 
     * @param storageSystem
     * @return
     */
    private Double getClusterStorageCapacity(final StorageSystem storageSystem) {
        Double cluserCap = 0.0;
        IsilonSshApi sshDmApi = new IsilonSshApi();
        sshDmApi.setConnParams(storageSystem.getIpAddress(), storageSystem.getUsername(),
                storageSystem.getPassword());
        cluserCap = sshDmApi.getClusterSize();
        return cluserCap;
    }

    /**
     * Create Virtual NAS for the specified Isilon cluster storage array
     * 
     * @param system
     * @param isiAccessZone
     * @return Virtual NAS Server
     */
    private VirtualNAS createVirtualNas(final StorageSystem system, final IsilonAccessZone isiAccessZone) {
        VirtualNAS vNas = new VirtualNAS();

        vNas.setStorageDeviceURI(system.getId());
        // set name
        vNas.setNasName(isiAccessZone.getName());
        vNas.setNativeId(isiAccessZone.getId());
        // set base directory path
        vNas.setBaseDirPath(isiAccessZone.getPath());
        vNas.setNasState(VirtualNasState.LOADED.toString());
        vNas.setId(URIUtil.createId(VirtualNAS.class));

        // set native "Guid"
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, isiAccessZone.getZone_id().toString(), NativeGUIDGenerator.VIRTUAL_NAS);
        vNas.setNativeGuid(nasNativeGuid);

        StringMap dbMetrics = vNas.getMetrics();
        _log.info("new Virtual NAS created with guid {} ", vNas.getNativeGuid());
        if (dbMetrics == null) {
            dbMetrics = new StringMap();
        }
        // set the Limitation Metrics keys
        setMaxDbMetricsAz(system, dbMetrics);
        vNas.setMetrics(dbMetrics);
        return vNas;
    }

    /**
     * Create Physical NAS for the specified Isilon cluster storage array
     * 
     * @param system
     * @param isiAccessZone
     * @return Physical NAS Server
     */
    private PhysicalNAS createPhysicalNas(final StorageSystem system, IsilonAccessZone isiAccessZone) {
        PhysicalNAS phyNas = new PhysicalNAS();

        phyNas.setStorageDeviceURI(system.getId());
        phyNas.setNasName(isiAccessZone.getName());
        phyNas.setNativeId(isiAccessZone.getId());
        // set base directory path

        phyNas.setNasState(VirtualNasState.LOADED.toString());
        phyNas.setId(URIUtil.createId(PhysicalNAS.class));
        // Set storage port details to vNas
        String physicalNasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, isiAccessZone.getZone_id().toString(), NativeGUIDGenerator.PHYSICAL_NAS);
        phyNas.setNativeGuid(physicalNasNativeGuid);
        _log.info("Physical NAS created with guid {} ", phyNas.getNativeGuid());

        StringMap dbMetrics = phyNas.getMetrics();
        if (dbMetrics == null) {
            dbMetrics = new StringMap();
        }
        // set the Limitation Metrics keys
        setMaxDbMetricsAz(system, dbMetrics);
        phyNas.setMetrics(dbMetrics);

        return phyNas;
    }

    /**
     * get the cifs servers for accesszone
     * 
     * @param isiAccessZone
     * @return cifs server map
     */
    CifsServerMap getCifsServerMap(final IsilonAccessZone isiAccessZone) {
        // add authentication map
        ArrayList<String> authArrayList = isiAccessZone.getAuth_providers();
        CifsServerMap cifsServersMap = new CifsServerMap();
        if (authArrayList != null && !authArrayList.isEmpty()) {
            for (String authProvider : authArrayList) {
                NasCifsServer nasCifsServer = new NasCifsServer();
                String[] providerArray = authProvider.split(":");
                nasCifsServer.setName(providerArray[0]);
                nasCifsServer.setDomain(providerArray[1]);
                cifsServersMap.put(providerArray[0], nasCifsServer);
            }
        }
        if (isiAccessZone.isAll_auth_providers() == true) {
            NasCifsServer nasCifsServer = new NasCifsServer();
            String[] providerArray = isiAccessZone.getSystem_provider().split(":");
            nasCifsServer.setName(providerArray[0]);
            nasCifsServer.setDomain(providerArray[1]);
            cifsServersMap.put(providerArray[0], nasCifsServer);
        }
        return cifsServersMap;
    }

    /**
     * Find the Virtual NAS by Native ID for Isilon cluster
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
        VirtualNAS tmpVnas = null;
        while (iter.hasNext()) {
            tmpVnas = _dbClient.queryObject(VirtualNAS.class, iter.next());

            if (tmpVnas != null && !tmpVnas.getInactive()) {
                vNas = tmpVnas;
                _log.info("found virtual NAS {}", tmpVnas.getNativeGuid() + ":" + tmpVnas.getNasName());
                break;
            }
        }

        return vNas;
    }

    /**
     * Find the Physical NAS by Native ID for Isilon cluster
     * 
     * @param system storage system information including credentials.
     * @param Native id of the specified Physical NAS
     * @return Physical NAS Server
     */
    private PhysicalNAS findPhysicalNasByNativeId(StorageSystem system, String nativeId) {
        PhysicalNAS physicalNas = null;
        URIQueryResultList results = new URIQueryResultList();
        // Set storage port details to vNas
        String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId, NativeGUIDGenerator.PHYSICAL_NAS);

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory.getPhysicalNasByNativeGuidConstraint(nasNativeGuid),
                results);
        PhysicalNAS tmpNas = null;
        Iterator<URI> iter = results.iterator();
        while (iter.hasNext()) {
            tmpNas = _dbClient.queryObject(PhysicalNAS.class, iter.next());

            if (tmpNas != null && !tmpNas.getInactive()) {
                physicalNas = tmpNas;
                _log.info("found physical NAS {}", physicalNas.getNativeGuid() + ":" + physicalNas.getNasName());
                break;
            }
        }

        return physicalNas;
    }

    /**
     * Find the Storageport by Native ID for given Isilon Cluster
     * 
     * @param system
     * @param nativeId
     * @return storageport object
     */
    private StoragePort findStoragePortByNativeId(StorageSystem system, String nativeId) {
        StoragePort storagePort = null;
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                system, nativeId,
                NativeGUIDGenerator.PORT);
        // Check if storage port was already discovered
        URIQueryResultList resultSetList = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getStoragePortByNativeGuidConstraint(portNativeGuid), resultSetList);
        StoragePort port = null;
        for (URI portUri : resultSetList) {
            port = _dbClient.queryObject(StoragePort.class, portUri);
            if (port != null) {
                if (port.getStorageDevice().equals(system.getId()) && !port.getInactive()) {
                    storagePort = port;
                    break;
                }
            }
        }
        return storagePort;
    }

}
