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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.emc.storageos.customconfigcontroller.CustomConfigConstants;
import com.emc.storageos.customconfigcontroller.DataSource;
import com.emc.storageos.customconfigcontroller.DataSourceFactory;
import com.emc.storageos.customconfigcontroller.impl.CustomConfigHandler;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.CifsServerMap;
import com.emc.storageos.db.client.model.CustomConfig;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.NASServer;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.PhysicalNAS;
import com.emc.storageos.db.client.model.QuotaDirectory;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedFileReplicationTypes;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.db.client.model.VirtualNAS.VirtualNasState;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileQuotaDirectory;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedNFSShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonAccessZone;
import com.emc.storageos.isilon.restapi.IsilonApi;
import com.emc.storageos.isilon.restapi.IsilonApi.IsilonLicenseType;
import com.emc.storageos.isilon.restapi.IsilonApi.IsilonList;
import com.emc.storageos.isilon.restapi.IsilonApiFactory;
import com.emc.storageos.isilon.restapi.IsilonClusterConfig;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.isilon.restapi.IsilonExport;
import com.emc.storageos.isilon.restapi.IsilonGroup;
import com.emc.storageos.isilon.restapi.IsilonNFSACL;
import com.emc.storageos.isilon.restapi.IsilonNFSACL.Persona;
import com.emc.storageos.isilon.restapi.IsilonNetworkPool;
import com.emc.storageos.isilon.restapi.IsilonPool;
import com.emc.storageos.isilon.restapi.IsilonSMBShare;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfo;
import com.emc.storageos.isilon.restapi.IsilonSmartConnectInfoV2;
import com.emc.storageos.isilon.restapi.IsilonSmartQuota;
import com.emc.storageos.isilon.restapi.IsilonSnapshot;
import com.emc.storageos.isilon.restapi.IsilonSshApi;
import com.emc.storageos.isilon.restapi.IsilonStoragePort;
import com.emc.storageos.isilon.restapi.IsilonSyncPolicy;
import com.emc.storageos.isilon.restapi.IsilonSyncTargetPolicy;
import com.emc.storageos.isilon.restapi.IsilonUser;
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
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Class for Isilon discovery and collecting stats from Isilon storage device
 */
public class IsilonCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private final Logger _log = LoggerFactory.getLogger(IsilonCommunicationInterface.class);
    private static final String POOL_TYPE = "IsilonNodePool";
    private static final int BYTESCONVERTER = 1024;
    private static final int PATH_IS_FILE = 1;
    private static final int PATH_IS_QUOTA = 2;
    private static final int PATH_IS_INVALID = 3;
    private static final String IFS_ROOT = "/ifs";
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
    private static final String SYSSECURITY = "sys";
    private static final String NFSv4 = "NFSv4";
    private static final String UMFS_DETAILS = "FS_DETAILS";
    private static final String UMFSQD_DETAILS = "UMFSQD_DETAILS";
    private static final String UMFS_QD_MAP = "UMFS_QD_MAP";
    private static final String SLASH = "/";
    private static final Long MAX_NFS_EXPORTS_V7_2 = 1500L;
    private static final Long MAX_CIFS_SHARES = 40000L;
    private static final Long MAX_STORAGE_OBJECTS = 40000L;
    private static final String SYSTEM_ACCESS_ZONE_NAME = "System";
    private static final Long KB_IN_BYTES = 1024L;
    private static final String ONEFS_V8 = "8.0.0.0";
    private static final String ONEFS_V7_2 = "7.2.0.0";
    private static final String ACTIVATED = "Activated";
    private static final String EVALUATION = "Evaluation";
    private IsilonApiFactory _factory;
    private static final String LICENSE_ACTIVATED = "Activated";
    private static final String LICENSE_EVALUATION = "Evaluation";
    private static final String CHECKPOINT_SCHEDULE = "checkpoint_schedule";
    private static final String ISILON_PATH_CUSTOMIZATION = "IsilonPathCustomization";
    private static final Integer MAX_RECORDS_SIZE = 100;

    private Set<String> _discPathsForUnManaged;
    private static String _discCustomPath;
    @Autowired
    private CustomConfigHandler customConfigHandler;
    @Autowired
    private DataSourceFactory dataSourceFactory;

    /**
     * Get Unmanaged File System Container paths
     * 
     * @return List object
     */
    public Set<String> getDiscPathsForUnManaged() {
        if (null == _discPathsForUnManaged) {
            _discPathsForUnManaged = new HashSet<>();

        }
        return _discPathsForUnManaged;
    }

    /**
     * Set Unmanaged File System Container paths
     * 
     * @param ;discPathsForUnManaged
     */
    public void setDiscPathsForUnManaged(Set<String> discPathsForUnManaged) {
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
     * Set the controller config info
     * 
     * @return
     */
    public void setCustomConfigHandler(CustomConfigHandler customConfigHandler) {
        this.customConfigHandler = customConfigHandler;
    }

    /**
     * Set the dataSource info
     * 
     * @return
     */
    public void setDataSourceFactory(DataSourceFactory dataSourceFactory) {
        this.dataSourceFactory = dataSourceFactory;
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
     * @param isilonCluster
     *            StorageDevice object
     * @return IsilonApi object
     * @throws IsilonException
     * @throws URISyntaxException
     */
    private IsilonApi getIsilonDevice(StorageSystem isilonCluster) throws IsilonException, URISyntaxException {
        URI deviceURI = new URI("https", null, isilonCluster.getIpAddress(), isilonCluster.getPortNumber(), "/", null, null);

        return _factory
                .getRESTClient(deviceURI, isilonCluster.getUsername(), isilonCluster.getPassword());
    }

    public Map<String, String> getStorageSystemFileShares(URI storageSystemURI) throws IOException {

        Map<String, String> fileSharesMap = new ConcurrentHashMap<String, String>();

        URIQueryResultList results = new URIQueryResultList();
        try {
            // Get File systems with given native id!!
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceFileshareConstraint(storageSystemURI),
                    results);

        } catch (Exception e) {
            _log.error(
                    "Exception while querying Fileshares from system: {}--> ",
                    storageSystemURI, e);
        }

        Iterator<FileShare> fileSystemItr = _dbClient.queryIterativeObjects(
                FileShare.class, results, true);
        while (fileSystemItr.hasNext()) {
            FileShare fileShare = fileSystemItr.next();
            if (fileShare != null && !fileShare.getInactive()) {
                fileSharesMap.put(fileShare.getNativeGuid(), fileShare.getId().toString());
            }
        }
        return fileSharesMap;
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
            boolean fsChanged = false;
            List<Stat> stats = new ArrayList<Stat>();
            List<FileShare> modifiedFileSystems = new ArrayList<FileShare>();

            ZeroRecordGenerator zeroRecordGenerator = new FileZeroRecordGenerator();
            CassandraInsertion statsColumnInjector = new FileDBInsertion();
            // get usage stats from quotas
            IsilonStatsRecorder recorder = new IsilonStatsRecorder(zeroRecordGenerator, statsColumnInjector);
            _keyMap.put(Constants._TimeCollected, System.currentTimeMillis());

            // compute static load processor code
            computeStaticLoadMetrics(storageSystemId);

            Map<String, String> fileSystemsMap = getStorageSystemFileShares(storageSystemId);
            if (fileSystemsMap.isEmpty()) {
                // No file shares for the storage system,
                // ignore stats collection for the system!!!
                _log.info("No file systems found for storage device {}. Hence metering stats collection ignored.", storageSystemId);
                return;
            }

            // Process IsilonQuotas page by page (MAX 1000) in a page...
            String resumeToken = null;
            do {
                IsilonApi.IsilonList<IsilonSmartQuota> quotas = api.listQuotas(resumeToken);
                resumeToken = quotas.getToken();
                for (IsilonSmartQuota quota : quotas.getList()) {
                    String fsNativeId = quota.getPath();
                    String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(deviceType, serialNumber, fsNativeId);
                    String fsId = fileSystemsMap.get(fsNativeGuid);
                    if (fsId == null || fsId.isEmpty()) {
                        // No file shares found for the quota
                        // ignore stats collection for the file system!!!
                        _log.debug("File System does not exists with nativeid {}. Hence ignoring stats collection.", fsNativeGuid);
                        continue;
                    }
                    Stat stat = recorder.addUsageStat(quota, _keyMap, fsId, api);
                    fsChanged = false;
                    if (null != stat) {
                        stats.add(stat);
                        // Persists the file system, only if change in used capacity.
                        FileShare fileSystem = _dbClient.queryObject(FileShare.class, stat.getResourceId());
                        if (fileSystem != null) {
                            if (!fileSystem.getInactive()) {
                                if (null != fileSystem.getUsedCapacity() && null != stat.getAllocatedCapacity() &&
                                        !fileSystem.getUsedCapacity().equals(stat.getAllocatedCapacity())) {
                                    fileSystem.setUsedCapacity(stat.getAllocatedCapacity());
                                    fsChanged = true;
                                }
                                if (null != fileSystem.getSoftLimit() && null != fileSystem.getSoftLimitExceeded() &&
                                        null != quota.getThresholds() && null != quota.getThresholds().getsoftExceeded() &&
                                        !fileSystem.getSoftLimitExceeded().equals(quota.getThresholds().getsoftExceeded())) {
                                    // softLimitExceeded
                                    fileSystem.setSoftLimitExceeded(quota.getThresholds().getsoftExceeded());
                                    fsChanged = true;
                                }
                                if (fsChanged) {
                                    modifiedFileSystems.add(fileSystem);
                                }
                            }
                        }
                    }

                    // Write the records batch wise!!
                    // Each batch with MAX_RECORDS_SIZE - 100 records!!!
                    if (modifiedFileSystems.size() >= MAX_RECORDS_SIZE) {
                        _dbClient.updateObject(modifiedFileSystems);
                        _log.info("Processed {} file systems stats ", modifiedFileSystems.size());
                        modifiedFileSystems.clear();
                    }

                    if (stats.size() >= MAX_RECORDS_SIZE) {
                        _log.info("Processed {} stats", stats.size());
                        persistStatsInDB(stats);
                    }
                }
                statsCount = statsCount + quotas.size();
                _log.info("Processed {} file system stats for device {} ", quotas.size(), storageSystemId);
            } while (resumeToken != null);

            zeroRecordGenerator.identifyRecordstobeZeroed(_keyMap, stats, FileShare.class);
            // write the remaining records!!
            if (!modifiedFileSystems.isEmpty()) {
                _dbClient.updateObject(modifiedFileSystems);
                _log.info("Processed {} file systems stats ", modifiedFileSystems.size());
                modifiedFileSystems.clear();
            }

            if (!stats.isEmpty()) {
                _log.info("Processed {} stats", stats.size());
                persistStatsInDB(stats);
            }
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

        // filesystems count & used Capacity
        IsilonList<IsilonSmartQuota> quotas = null;

        do {
            quotas = isilonApi.listQuotas(resumeToken, baseDirPath);

            if (quotas != null && !quotas.getList().isEmpty()) {
                for (IsilonSmartQuota quota : quotas.getList()) {

                    totalProvCap = totalProvCap + quota.getUsagePhysical();
                    totalFsCount++;
                }
            }
            resumeToken = quotas.getToken();

        } while (resumeToken != null);

        // create a list of access zone for which base dir is not same as system access zone.
        // we get all snapshot list at once. baseDirPaths list is used to
        // find snaphot belong to which access zone.
        List<String> baseDirPaths = null;
        if (accessZone.isSystem() == true) {
            List<IsilonAccessZone> isilonAccessZoneList = isilonApi.getAccessZones(resumeToken);
            baseDirPaths = new ArrayList<String>();
            for (IsilonAccessZone isiAccessZone : isilonAccessZoneList) {

                if (!baseDirPath.equals(IFS_ROOT + "/")) {
                    baseDirPaths.add(isiAccessZone.getPath() + "/");
                }
            }
        }
        // snapshots count & snap capacity
        resumeToken = null;
        IsilonList<IsilonSnapshot> snapshots = null;
        do {
            snapshots = isilonApi.listSnapshots(resumeToken);
            if (snapshots != null && !snapshots.getList().isEmpty()) {

                if (!baseDirPath.equals(IFS_ROOT + "/")) {
                    // if it is not system access zone then compare
                    // with fs path with base dir path
                    _log.info("access zone base directory path {}", baseDirPath);
                    for (IsilonSnapshot isilonSnap : snapshots.getList()) {
                        if (isilonSnap.getPath().startsWith(baseDirPath)) {
                            totalProvCap = totalProvCap + Long.valueOf(isilonSnap.getSize());
                            totalFsCount++;
                        }
                    }
                } else {// process the snapshots for system access zone
                    boolean snapSystem = true;
                    for (IsilonSnapshot isilonSnap : snapshots.getList()) {
                        snapSystem = true;
                        // first check fs path with user defined AZ's paths
                        if (baseDirPaths != null && !baseDirPaths.isEmpty()) {
                            for (String basePath : baseDirPaths) {
                                if (isilonSnap.getPath().startsWith(basePath)) {
                                    snapSystem = false;
                                    break;
                                }
                            }
                        }

                        // it then it is belongs to access zone with basedir same as system access zone.
                        if (snapSystem) {
                            totalProvCap = totalProvCap + Long.valueOf(isilonSnap.getSize());
                            totalFsCount++;
                            _log.info("Access zone base directory path: {}", accessZone.getPath());

                        }
                    }
                }
                resumeToken = snapshots.getToken();
            }
        } while (resumeToken != null);

        if (totalProvCap > 0) {
            totalProvCap = (totalProvCap / KB_IN_BYTES);
        }
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
            _dbClient.updateObject(storageSystem);
            if (!storageSystem.getReachableStatus()) {
                // TODO Need to use non-deprecated constructor..
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
                _dbClient.updateObject(pools.get(EXISTING));
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
                _dbClient.updateObject(ports.get(EXISTING));
            }
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                    _dbClient, storageSystemId);
            List<StoragePort> allExistPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            allExistPorts.addAll(notVisiblePorts);
            _completer.statusPending(_dbClient, "Completed port discovery");

            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW),
                    allExistPorts, _dbClient, _coordinator, poolsToMatchWithVpool);
            // discover the access zone and its network interfaces
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
                    _dbClient.updateObject(storageSystem);
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

        List<VirtualNAS> newvNASList = new ArrayList<VirtualNAS>();
        List<VirtualNAS> existingvNASList = new ArrayList<VirtualNAS>();

        List<PhysicalNAS> newPhysicalNASList = new ArrayList<PhysicalNAS>();
        List<PhysicalNAS> existingPhysicalNASList = new ArrayList<PhysicalNAS>();

        List<VirtualNAS> discoveredVNASList = new ArrayList<VirtualNAS>();

        // Discover storage ports
        try {
            _log.info("discoverAccessZones for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            // Make restapi call to get access zones
            List<IsilonAccessZone> accessZoneList = isilonApi.getAccessZones(null);
            if (accessZoneList == null || accessZoneList.isEmpty()) {
                // No access zones defined. Throw an exception and fail the discovery
                IsilonCollectionException ice = new IsilonCollectionException("discoverAccessZones failed. No Zones defined");
                throw ice;
            }

            // Find the smart connect zones
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

            List<IsilonNetworkPool> isilonNetworkPools = null;

            // process the access zones list
            for (IsilonAccessZone isilonAccessZone : accessZoneList) {
                // add protocol to NAS servers
                // is it a System access zone?
                isilonNetworkPools = null;

                if (!isilonAccessZone.isSystem()) {
                    _log.info("Process the user defined access zone {} ", isilonAccessZone.toString());
                    isilonNetworkPools = new ArrayList<IsilonNetworkPool>();
                    // get the smart connect zone information
                    for (IsilonNetworkPool eachNetworkPool : isilonNetworkPoolList) {
                        if (eachNetworkPool.getAccess_zone().equalsIgnoreCase(isilonAccessZone.getName())) {
                            isilonNetworkPools.add(eachNetworkPool);
                        }
                    }

                    // find virtualNAS in db
                    virtualNAS = findvNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    if (virtualNAS == null) {
                        if (isilonNetworkPools != null && !isilonNetworkPools.isEmpty()) {
                            virtualNAS = createVirtualNas(storageSystem, isilonAccessZone);
                            newvNASList.add(virtualNAS);
                        }
                    } else {
                        copyUpdatedPropertiesInVNAS(storageSystem, isilonAccessZone, virtualNAS);
                        existingvNASList.add(virtualNAS);

                    }

                    // Set authentication providers
                    setCifsServerMapForNASServer(isilonAccessZone, virtualNAS);

                    // set protocol support
                    if (virtualNAS != null) {
                        virtualNAS.setProtocols(protocols);
                    }

                    // set the smart connect
                    setStoragePortsForNASServer(isilonNetworkPools, storageSystem, virtualNAS);

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
                    // Set authentication providers
                    setCifsServerMapForNASServer(isilonAccessZone, physicalNAS);

                    // set the smart connect zone
                    setStoragePortsForNASServer(isilonNetworkPoolsSysAZ, storageSystem, physicalNAS);
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
                _log.info("Modified Virtual NAS servers size {}", existingvNASList.size());
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

    private void setStoragePortsForNASServer(List<IsilonNetworkPool> isilonNetworkPools,
            StorageSystem storageSystem, NASServer nasServer) {

        if (nasServer == null) {
            return;
        }

        StringSet storagePorts = nasServer.getStoragePorts();

        if (storagePorts == null) {
            storagePorts = new StringSet();
        } else {
            storagePorts.clear();
        }

        if (isilonNetworkPools != null && !isilonNetworkPools.isEmpty()) {

            for (IsilonNetworkPool isiNetworkPool : isilonNetworkPools) {
                StoragePort storagePort = findStoragePortByNativeId(storageSystem,
                        isiNetworkPool.getSc_dns_zone());
                if (storagePort != null) {
                    storagePorts.add(storagePort.getId().toString());
                }
            }
            if (DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name().equals(nasServer.getDiscoveryStatus())) {
                _log.info("Setting discovery status of vnas {} as VISIBLE", nasServer.getNasName());
                nasServer.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
            }
            if (VirtualNAS.VirtualNasState.UNKNOWN.name().equals(nasServer.getNasState())) {
                _log.info("Setting state of vnas {} as LOADED", nasServer.getNasName());
                nasServer.setNasState(VirtualNAS.VirtualNasState.LOADED.name());
            }
        } else {
            /*
             * Smart connect zones are dissociated with this access zone.
             * So mark this access zone as not visible.
             */
            _log.info("Setting discovery status of vnas {} as NOTVISIBLE", nasServer.getNasName());
            nasServer.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.NOTVISIBLE.name());
            nasServer.setNasState(VirtualNAS.VirtualNasState.UNKNOWN.name());
            StringSet assignedVarrays = nasServer.getAssignedVirtualArrays();
            if (assignedVarrays != null) {
                nasServer.removeAssignedVirtualArrays(assignedVarrays);
            }
        }

        _log.info("Setting storage ports for vNAS [{}] : {}", nasServer.getNasName(), storagePorts);
        nasServer.setStoragePorts(storagePorts);
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
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, clusterReleaseVersion) < 0) {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                IsilonCollectionException ice = new IsilonCollectionException(String.format(
                        " ** This version of Isilon firmware is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw ice;
            }
            storageSystem.setSupportSoftLimit(false);
            storageSystem.setSupportNotificationLimit(false);
            // Check license status for smart quota and set the support attributes as true
            if (ACTIVATED.equalsIgnoreCase(isilonApi.getLicenseInfo(IsilonLicenseType.SMARTQUOTA))
                    || EVALUATION.equalsIgnoreCase(isilonApi.getLicenseInfo(IsilonLicenseType.SMARTQUOTA))) {
                storageSystem.setSupportSoftLimit(true);
                storageSystem.setSupportNotificationLimit(true);
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
            boolean isNfsV4Enabled = isilonApi.nfsv4Enabled(storageSystem.getFirmwareVersion());
            boolean syncLicenseValid = isValidLicense(isilonApi.getReplicationLicenseInfo());
            boolean snapLicenseValid = isValidLicense(isilonApi.snapshotIQLicenseInfo());

            // Set file replication type for Isilon storage system!!!
            if (syncLicenseValid) {
                StringSet supportReplicationTypes = new StringSet();
                supportReplicationTypes.add(SupportedFileReplicationTypes.REMOTE.name());
                supportReplicationTypes.add(SupportedFileReplicationTypes.LOCAL.name());
                storageSystem.setSupportedReplicationTypes(supportReplicationTypes);
            }

            _log.info("Isilon OneFS version: {}", storageSystem.getFirmwareVersion());
            List<? extends IsilonPool> isilonPools = null;
            if (VersionChecker.verifyVersionDetails(ONEFS_V7_2, storageSystem.getFirmwareVersion()) >= 0) {
                _log.info("Querying for Isilon storage pools...");
                isilonPools = isilonApi.getStoragePools();
            } else {
                _log.info("Querying for Isilon disk pools...");
                isilonPools = isilonApi.getDiskPools();
            }

            for (IsilonPool isilonPool : isilonPools) {
                // Check if this storage pool was already discovered
                StoragePool storagePool = null;
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, isilonPool.getNativeId(),
                        NativeGUIDGenerator.POOL);

                URIQueryResultList poolURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(poolNativeGuid), poolURIs);

                for (URI poolUri : poolURIs) {
                    StoragePool pool = _dbClient.queryObject(StoragePool.class, poolUri);
                    if (!pool.getInactive() && pool.getStorageDevice().equals(storageSystemId)) {
                        storagePool = pool;
                        break;
                    }
                }

                if (storagePool == null) {
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

                // Add the Copy type ASYNC, if the Isilon is enabled with SyncIQ service!!
                StringSet copyTypesSupported = new StringSet();

                if (syncLicenseValid) {
                    copyTypesSupported.add(CopyTypes.ASYNC.name());
                    storagePool.setSupportedCopyTypes(copyTypesSupported);
                } else {
                    if (storagePool.getSupportedCopyTypes() != null &&
                            storagePool.getSupportedCopyTypes().contains(CopyTypes.ASYNC.name())) {
                        storagePool.getSupportedCopyTypes().remove(CopyTypes.ASYNC.name());
                    }
                }

                // Add the Copy type ScheduleSnapshot, if the Isilon is enabled with SnapshotIQ
                if (snapLicenseValid) {
                    copyTypesSupported.add(CHECKPOINT_SCHEDULE);
                    storagePool.setSupportedCopyTypes(copyTypesSupported);
                } else {
                    if (storagePool.getSupportedCopyTypes() != null &&
                            storagePool.getSupportedCopyTypes().contains(CHECKPOINT_SCHEDULE)) {
                        storagePool.getSupportedCopyTypes().remove(CHECKPOINT_SCHEDULE);
                    }
                }

                // scale capacity size
                storagePool.setFreeCapacity(isilonPool.getAvailableBytes() / BYTESCONVERTER);
                storagePool.setTotalCapacity(isilonPool.getTotalBytes() / BYTESCONVERTER);
                storagePool.setSubscribedCapacity(isilonPool.getUsedBytes() / BYTESCONVERTER);
                if (ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getCompatibilityStatus(),
                        DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getDiscoveryStatus(),
                                DiscoveryStatus.VISIBLE.name())) {
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
                    // TODO Better to throw some less generic exception..
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
                URIQueryResultList portURIs = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePortByNativeGuidConstraint(portNativeGuid), portURIs);

                for (URI portUri : portURIs) {
                    StoragePort port = _dbClient.queryObject(StoragePort.class, portUri);
                    if (port.getStorageDevice().equals(storageSystemId) && !port.getInactive()) {
                        storagePort = port;
                        break;
                    }
                }
                if (storagePort == null) {
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
     * get user define the access zone location separated by comma
     * 
     * @param nasServers
     */
    String getUserAccessZonePath(Map<String, NASServer> nasServers) {
        String accessZonePath = ",";
        // Initialized with comma as empty can lead to exception at controller configuration.
        if (nasServers != null && !nasServers.isEmpty()) {
            for (String path : nasServers.keySet()) {
                String nasType = URIUtil.getTypeName(nasServers.get(path).getId());
                if (StringUtils.isNotEmpty(path) && StringUtils.equals(nasType, "VirtualNAS")) {
                    accessZonePath = accessZonePath + path + ",";
                }
            }
        }
        return accessZonePath;
    }

    /**
     * Add custom discovery directory paths from controller configuration
     */
    private void updateDiscoveryPathForUnManagedFS(Map<String, NASServer> nasServer, StorageSystem storage)
            throws IsilonCollectionException {
        String paths = "";
        String systemAccessZone = "";
        String userAccessZone = "";
        String namespace = "";
        String customLocations = ",";

        // get the system access zones
        DataSource ds = new DataSource();
        ds.addProperty(CustomConfigConstants.ISILON_NO_DIR, "");
        ds.addProperty(CustomConfigConstants.ISILON_DIR_NAME, "");
        namespace = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.ISILON_SYSTEM_ACCESS_ZONE_NAMESPACE, "isilon",
                ds);
        namespace = namespace.replaceAll("=", "");
        if (namespace.isEmpty()) {
            systemAccessZone = IFS_ROOT + "/";
        } else {
            systemAccessZone = IFS_ROOT + "/" + namespace + "/";
        }
        // get the user access zone
        userAccessZone = getUserAccessZonePath(nasServer);
        // create a dataSouce and place the value for system and user access zone
        DataSource dataSource = dataSourceFactory.createIsilonUnmanagedFileSystemLocationsDataSource(storage);
        dataSource.addProperty(CustomConfigConstants.ISILON_SYSTEM_ACCESS_ZONE, systemAccessZone);
        dataSource.addProperty(CustomConfigConstants.ISILON_USER_ACCESS_ZONE, userAccessZone);
        dataSource.addProperty(CustomConfigConstants.ISILON_CUSTOM_DIR_PATH, customLocations);

        paths = customConfigHandler.getComputedCustomConfigValue(CustomConfigConstants.ISILON_UNMANAGED_FILE_SYSTEM_LOCATIONS,
                "isilon",
                dataSource);
        // trim leading or trailing or multiple comma.
        paths = paths.replaceAll("^,+", "").replaceAll(",+$", "").replaceAll(",+", ",");
        if (paths.equals(",") || paths.isEmpty()) {
            IsilonCollectionException ice = new IsilonCollectionException(
                    "computed unmanaged file system location is empty. Please verify Isilon controller config settings");
            throw ice;
        }
        _log.info("Unmanaged file system locations are {}", paths);
        List<String> pathList = Arrays.asList(paths.split(","));
        Set<String> pathSet = new HashSet<>();
        pathSet.addAll(pathList);

        setDiscPathsForUnManaged(pathSet);

        _discCustomPath = getCustomConfigPath();
    }

    /**
     * Check license is valid or not
     * 
     * @param licenseStatus
     *            Status of the license
     * @return true/false
     * @throws IsilonException
     * @throws JSONException
     */
    private boolean isValidLicense(String licenseStatus) {
        Set<String> validLicenseStatus = new HashSet<String>();
        validLicenseStatus.add(LICENSE_ACTIVATED);
        validLicenseStatus.add(LICENSE_EVALUATION);

        if (validLicenseStatus.contains(licenseStatus)) {
            return true;
        }
        return false;
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

                if (!SYSTEM_ACCESS_ZONE_NAME.equals(entry.getValue().getNasName()) && fsPath.startsWith(entry.getKey())) {
                    nasServer = entry.getValue();
                    break;
                }
            }
            if (nasServer == null) {
                nasServer = nasServerMap.get(IFS_ROOT + "/");
            }
        }

        return nasServer;
    }

    /*
     * The method finds the replication policy for file system directory
     * The policy might be at either file system level or at higher directory level
     * policy name with _mirror suffix should not be considered for source as they represent for target
     */
    private boolean setSourceReplicationPolicyAttributes(UnManagedFileSystem unManagedFs, String fsPath,
            ArrayList<IsilonSyncPolicy> isiSyncIQPolicies) {
        StringSet targetPaths = new StringSet();
        StringSet targetHosts = new StringSet();
        StringSet policySourcePath = new StringSet();
        StringSet policySchedule = new StringSet();

        if (fsPath != null && !fsPath.isEmpty()) {
            for (IsilonSyncPolicy isiSyncIQPolicy : isiSyncIQPolicies) {
                // Leave the mirror policies as they represent as targets!!
                // Local target policies are processed for target file systems.
                if (isiSyncIQPolicy.getName() != null && isiSyncIQPolicy.getName().endsWith("_mirror")) {
                    _log.debug("Policy {} is a target policy, not for source file system", isiSyncIQPolicy.getName());
                    continue;
                }
                if (isiSyncIQPolicy.getSourceRootPath() != null && !isiSyncIQPolicy.getSourceRootPath().isEmpty()) {
                    String policyPath = isiSyncIQPolicy.getSourceRootPath();
                    // Add SLASH to end of the path,
                    // it would be easy to verifying policy at fs level or higher level
                    policyPath = policyPath + (policyPath.endsWith(SLASH) ? "" : SLASH);
                    fsPath = fsPath + (fsPath.endsWith(SLASH) ? "" : SLASH);
                    // If policy at file system level, both policy path and fs path should be same.
                    // if policy at higher directory level of this file system,
                    // the policy path should be part of file system path.
                    if (policyPath.equals(fsPath) || fsPath.startsWith(policyPath)) {
                        unManagedFs.putFileSystemCharacterstics(
                                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_MIRROR_SOURCE.toString(), TRUE);
                        // Add the policy attributes to UMFS object
                        targetPaths.add(isiSyncIQPolicy.getTargetPath());
                        targetHosts.add(isiSyncIQPolicy.getTargetHost());
                        policySourcePath.add(isiSyncIQPolicy.getSourceRootPath());
                        policySchedule.add(isiSyncIQPolicy.getSchedule());

                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.TARGET_HOST.toString(),
                                targetHosts);
                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.TARGET_PATH.toString(),
                                targetPaths);
                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.POLICY_PATH.toString(),
                                policySourcePath);
                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.POLICY_SCHEDULE.toString(),
                                policySchedule);
                        return true;
                    }
                } else {
                    _log.debug("Policy {} source directory path is empty ", isiSyncIQPolicy.getName());
                }

            }
        }
        return false;
    }

    /*
     * The method finds the replication local target policy for file system directory.
     * The policy might be at either file system level or at higher directory level.
     * As these policies are local targets, policy name with _mirror suffix should not be
     * considered for target as they represent for source.
     */
    private boolean setTargetReplicationPolicyAttributes(UnManagedFileSystem unManagedFs, String fsPath,
            ArrayList<IsilonSyncTargetPolicy> isiSyncIQPolicies) {
        StringSet sourceHosts = new StringSet();
        StringSet policyDirPath = new StringSet();
        StringSet policySchedule = new StringSet();

        if (fsPath != null && !fsPath.isEmpty()) {
            for (IsilonSyncTargetPolicy localTargetPolicy : isiSyncIQPolicies) {
                // Leave the mirror policies as they represent as targets!!
                // Local target policies are processed for target file systems.
                if (localTargetPolicy.getName() != null && localTargetPolicy.getName().endsWith("_mirror")) {
                    _log.debug("Local target policy {} is a source policy, not for target file system", localTargetPolicy.getName());
                    continue;
                }
                if (localTargetPolicy.getTargetPath() != null && !localTargetPolicy.getTargetPath().isEmpty()) {
                    String policyPath = localTargetPolicy.getTargetPath();
                    // Add SLASH to end of the path, if not
                    // it would be easy to verifying policy at fs level or higher level
                    policyPath = policyPath + (policyPath.endsWith(SLASH) ? "" : SLASH);
                    fsPath = fsPath + (fsPath.endsWith(SLASH) ? "" : SLASH);
                    // If policy at file system level, both policy path and fs path should be same.
                    // if policy at higher directory level of this file system,
                    // the policy path should be part of file system path.
                    if (policyPath.equals(fsPath) || fsPath.startsWith(policyPath)) {
                        unManagedFs.putFileSystemCharacterstics(
                                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_MIRROR_TARGET.toString(), TRUE);
                        // Add the policy attributes to UMFS object
                        sourceHosts.add(localTargetPolicy.getSourceHost());
                        policyDirPath.add(localTargetPolicy.getTargetPath());
                        policySchedule.add(localTargetPolicy.getSchedule());

                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.SOURCE_HOST.toString(),
                                sourceHosts);
                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.POLICY_PATH.toString(),
                                policyDirPath);
                        unManagedFs.putFileSystemInfo(UnManagedFileSystem.SupportedFileSystemInformation.POLICY_SCHEDULE.toString(),
                                policySchedule);
                        return true;
                    }
                } else {
                    _log.debug("Policy {} source directory path is empty ", localTargetPolicy.getName());
                }

            }
        }
        return false;
    }

    private void discoverUmanagedFileSystems(AccessProfile profile) throws BaseCollectionException {

        List<UnManagedFileSystem> newUnManagedFileSystems = new ArrayList<>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<>();

        List<UnManagedFileQuotaDirectory> newUnManagedFileQuotaDir = new ArrayList<>();
        List<UnManagedFileQuotaDirectory> existingUnManagedFileQuotaDir = new ArrayList<>();

        List<UnManagedCifsShareACL> newUnManagedCifsShareACLList = new ArrayList<>();
        List<UnManagedCifsShareACL> oldUnManagedCifsShareACLList = new ArrayList<>();

        List<UnManagedNFSShareACL> newUnManagedNfsShareACLList = new ArrayList<>();
        List<UnManagedNFSShareACL> oldUnManagedNfsShareACLList = new ArrayList<>();

        List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<>();
        List<UnManagedFileExportRule> oldUnManagedExportRules = new ArrayList<>();

        _log.debug("Access Profile Details :  IpAddress : PortNumber : {}, namespace : {}",
                profile.getIpAddress() + profile.getPortNumber(),
                profile.getnamespace());

        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<>();

        String detailedStatusMessage = "Discovery of Isilon Unmanaged FileSystem started";
        long unmanagedFsCount = 0;
        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);

            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);

            ArrayList<StoragePool> pools = new ArrayList<>();
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

            int totalIsilonFSDiscovered = 0;

            // get the associated storage port for vnas Server
            List<IsilonAccessZone> isilonAccessZones = isilonApi.getAccessZones(null);
            Map<String, NASServer> nasServers = validateAndGetNASServer(storageSystem, isilonAccessZones);

            // update the path from controller configuration
            updateDiscoveryPathForUnManagedFS(nasServers, storageSystem);

            // NFSv4 enabled on storage system!!!
            boolean isNfsV4Enabled = isilonApi.nfsv4Enabled(storageSystem.getFirmwareVersion());

            // Get the list of SyncIQ policies present in the system!!
            ArrayList<IsilonSyncPolicy> isiSyncIQPolicies = isilonApi.getReplicationPolicies().getList();

            // Get the list of SyncIQ local target policies present in the system!!
            ArrayList<IsilonSyncTargetPolicy> isiSyncIQLocalTargetPolicies = isilonApi.getTargetReplicationPolicies().getList();

            List<FileShare> discoveredFS = new ArrayList<>();
            String resumeToken = null;

            for (String umfsDiscoverPath : _discPathsForUnManaged) {

                IsilonAccessZone isilonAccessZone = getAccessZoneCorresDiscoveryPath(isilonAccessZones, umfsDiscoverPath);
                String isilonAccessZoneName;
                if (isilonAccessZone == null) {
                    // System access zone
                    isilonAccessZoneName = null;
                } else {
                    isilonAccessZoneName = isilonAccessZone.getName();
                }

                // Get All SMB for this path access zone
                HashMap<String, HashSet<String>> zoneSMBShares = discoverAccessZoneSMBShares(storageSystem, isilonAccessZoneName);

                // Get all NFS Export for this path access zone
                HashMap<String, HashSet<Integer>> zoneNFSExports = discoverAccessZoneExports(storageSystem, isilonAccessZoneName);

                do {
                    HashMap<String, Object> discoverdFileDetails = discoverAllFileSystem(storageSystem, resumeToken,
                            umfsDiscoverPath);

                    IsilonApi.IsilonList<FileShare> discoveredIsilonFS = (IsilonApi.IsilonList<FileShare>) discoverdFileDetails
                            .get(UMFS_DETAILS);

                    ArrayList<UnManagedFileQuotaDirectory> discoveredUmfsQd = (ArrayList<UnManagedFileQuotaDirectory>) discoverdFileDetails
                            .get(UMFSQD_DETAILS);

                    HashMap<String, HashSet<String>> umfsfileQuotaMap = (HashMap<String, HashSet<String>>) discoverdFileDetails
                            .get(UMFS_QD_MAP);

                    resumeToken = discoveredIsilonFS.getToken();
                    discoveredFS = discoveredIsilonFS.getList();

                    totalIsilonFSDiscovered += discoveredFS.size();

                    for (FileShare fs : discoveredFS) {

                        if (!DiscoveryUtils.isUnmanagedVolumeFilterMatching(fs.getName())) {
                            // skipping this file system because the filter doesn't match
                            continue;
                        }

                        if (!checkStorageFileSystemExistsInDB(fs.getNativeGuid())) {

                            // Create UnManaged FS
                            String fsPathName = fs.getPath();
                            UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fs.getNativeGuid());

                            if (unManagedFs != null) {
                                existingUnManagedFileSystems.add(unManagedFs);
                            }

                            // get the matched vNAS Server
                            NASServer nasServer = getMatchedNASServer(nasServers, fsPathName);
                            if (nasServer != null) {
                                // Get valid storage port from the NAS server!!!
                                _log.info("fs path {} and nas server details {}", fs.getPath(), nasServer.toString());
                                storagePort = getStoragePortFromNasServer(nasServer);
                                if (storagePort == null) {
                                    _log.info("No valid storage port found for nas server {}", nasServer.toString());
                                    continue;
                                }
                            } else {
                                _log.info("fs path {} and vnas server not found", fs.getPath());
                                continue; // Skip further ingestion steps on this file share & move to next file share
                            }

                            unManagedFs = createUnManagedFileSystem(unManagedFs,
                                    fs.getNativeGuid(), storageSystem, storagePool, nasServer, fs);

                            // Set the policy attributes!!
                            if (setSourceReplicationPolicyAttributes(unManagedFs, fs.getPath(), isiSyncIQPolicies)) {
                                _log.info("File system {} is a source fs ", fs.getPath());
                                DiscoveryUtils.filterSupportedVpoolsBasedOnFileReplication(unManagedFs, _dbClient);
                            } else if (setTargetReplicationPolicyAttributes(unManagedFs, fs.getPath(), isiSyncIQLocalTargetPolicies)) {
                                _log.info("File system {} is a target fs ", fs.getPath());
                                DiscoveryUtils.filterSupportedVpoolsBasedOnFileReplication(unManagedFs, _dbClient);
                            } else {
                                _log.debug("File system {} is a not enabled with replication ", fs.getPath());
                            }

                            unManagedFs.setHasNFSAcl(false);
                            newUnManagedFileSystems.add(unManagedFs);

                            /**
                             * Set and create the NFS ACLs only if the system is enabled with NFSv4!!!
                             */
                            HashMap<String, HashSet<Integer>> exportWithIdMap = getExportsIncludingSubDir(fs.getPath(), zoneNFSExports,
                                    umfsfileQuotaMap);

                            if (isNfsV4Enabled) {
                                Set<String> fsNfsACLPaths = new HashSet<String>();
                                // need to consider at file system level and its all export level subDir paths.
                                fsNfsACLPaths.addAll(exportWithIdMap.keySet());
                                fsNfsACLPaths.add(fs.getPath());
                                setUnmanagedFileSystemNfsACL(unManagedFs, storageSystem, isilonApi, fsNfsACLPaths,
                                        newUnManagedNfsShareACLList,
                                        oldUnManagedNfsShareACLList, isilonAccessZoneName);
                            }

                            /**
                             * Set and Create Export Rules and export Map
                             */
                            if (!exportWithIdMap.keySet().isEmpty()) {
                                setUnManagedFSExportMap(unManagedFs, exportWithIdMap, storagePort,
                                        fs.getPath(), nasServer.getNasName(), isilonApi, storageSystem, newUnManagedExportRules,
                                        oldUnManagedExportRules);
                                _log.info("Number of exports discovered for file system {} is {}", unManagedFs.getId(),
                                        newUnManagedExportRules.size());
                                if (!newUnManagedExportRules.isEmpty()) {
                                    unManagedFs.setHasExports(true);
                                    _log.info("File System {} has Exports and their size is {}", unManagedFs.getId(),
                                            newUnManagedExportRules.size());
                                }
                            }

                            /**
                             * Create and set CIFS ACLS and SMB Share MAP
                             */
                            HashSet<String> shareIDs = getSharesIncludingSubDir(fs.getPath(), zoneSMBShares,
                                    umfsfileQuotaMap);
                            setUnmanagedCifsShareACL(unManagedFs, shareIDs,
                                    newUnManagedCifsShareACLList, storagePort, fs.getName(), nasServer.getNasName(),
                                    storageSystem, isilonApi, oldUnManagedCifsShareACLList);
                            _log.info("Number of shares ACLs discovered for file system {} is {}", unManagedFs.getId(),
                                    newUnManagedCifsShareACLList.size());

                            if (unManagedFs.getHasExports() || unManagedFs.getHasShares()) {
                                _log.info("FS {} is having exports/shares", fs.getPath());
                                unManagedFs.putFileSystemCharacterstics(
                                        UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED.toString(), TRUE);
                            } else {
                                _log.info("FS {} does not have export or share", fs.getPath());
                            }

                            /**
                             * Persist 200 objects and clear them to avoid memory issue
                             */
                            // save bunch of export rules in db
                            validateSizeLimitAndPersist(newUnManagedExportRules, oldUnManagedExportRules,
                                    Constants.DEFAULT_PARTITION_SIZE * 2);

                            // save bunch of ACLs in db
                            validateSizeLimitAndPersist(newUnManagedCifsShareACLList, oldUnManagedCifsShareACLList,
                                    Constants.DEFAULT_PARTITION_SIZE * 2);

                            // save bunch of NFS ACLs in db
                            validateSizeLimitAndPersist(newUnManagedNfsShareACLList, oldUnManagedNfsShareACLList,
                                    Constants.DEFAULT_PARTITION_SIZE * 2);

                            allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());

                            // save bunch of file system in db
                            validateListSizeLimitAndPersist(newUnManagedFileSystems, existingUnManagedFileSystems,
                                    Constants.DEFAULT_PARTITION_SIZE * 2);
                        }
                    }

                    /**
                     * Create and set Quota Directory for this file system..
                     */

                    for (UnManagedFileQuotaDirectory umfsQd : discoveredUmfsQd) {
                        if (!checkStorageQuotaDirectoryExistsInDB(umfsQd.getNativeGuid())) {

                            String fsUnManagedQdNativeGuid = NativeGUIDGenerator.generateNativeGuidForUnManagedQuotaDir(
                                    storageSystem.getSystemType(), storageSystem.getSerialNumber(), umfsQd.getNativeId(), "");

                            UnManagedFileQuotaDirectory unManagedFileQd = checkUnManagedFileSystemQuotaDirectoryExistsInDB(
                                    fsUnManagedQdNativeGuid);

                            boolean umfsQdExists = (unManagedFileQd == null) ? false : true;
                            if (umfsQdExists) {
                                umfsQd.setId(unManagedFileQd.getId());
                                existingUnManagedFileQuotaDir.add(umfsQd);
                            } else if (null != umfsQd) {
                                umfsQd.setId(URIUtil.createId(UnManagedFileQuotaDirectory.class));
                                newUnManagedFileQuotaDir.add(umfsQd);
                            }
                        }
                    }

                    // save bunch of QDs in db
                    validateSizeLimitAndPersist(newUnManagedFileQuotaDir, existingUnManagedFileQuotaDir,
                            Constants.DEFAULT_PARTITION_SIZE * 2);

                } while (resumeToken != null);
            }

            // Saving bunch of Unmanaged objects!!!
            if (!newUnManagedExportRules.isEmpty()) {
                _log.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                _dbClient.createObject(newUnManagedExportRules);
                newUnManagedExportRules.clear();
            }

            if (!oldUnManagedExportRules.isEmpty()) {
                _log.info("Saving Number of UnManagedFileExportRule(s) {}", oldUnManagedExportRules.size());
                _dbClient.updateObject(oldUnManagedExportRules);
                oldUnManagedExportRules.clear();
            }

            // save ACLs in db
            if (!newUnManagedCifsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedCifsShareACL(s) {}", newUnManagedCifsShareACLList.size());
                _dbClient.createObject(newUnManagedCifsShareACLList);
                newUnManagedCifsShareACLList.clear();
            }

            // save old acls
            if (!oldUnManagedCifsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedFileExportRule(s) {}", oldUnManagedCifsShareACLList.size());
                _dbClient.updateObject(oldUnManagedCifsShareACLList);
                oldUnManagedCifsShareACLList.clear();
            }

            // save NFS ACLs in db
            if (!newUnManagedNfsShareACLList.isEmpty()) {
                _log.info("Saving Number of UnManagedNfsShareACL(s) {}", newUnManagedNfsShareACLList.size());
                _dbClient.createObject(newUnManagedNfsShareACLList);
                newUnManagedNfsShareACLList.clear();
            }

            // save old acls
            if (!oldUnManagedNfsShareACLList.isEmpty()) {
                _log.info("Saving Number of NFS UnManagedFileExportRule(s) {}", oldUnManagedNfsShareACLList.size());
                _dbClient.updateObject(oldUnManagedNfsShareACLList);
                oldUnManagedNfsShareACLList.clear();
            }

            // save new QDs to DB
            if (!newUnManagedFileQuotaDir.isEmpty()) {
                _log.info("New unmanaged Isilon file systems QuotaDirecotry  count: {}", newUnManagedFileQuotaDir.size());
                _dbClient.createObject(newUnManagedFileQuotaDir);
            }

            // save old QDs
            if (!existingUnManagedFileQuotaDir.isEmpty()) {
                _log.info("Update unmanaged Isilon file systems QuotaDirectory count: {}",
                        existingUnManagedFileQuotaDir.size());
                _dbClient.updateObject(existingUnManagedFileQuotaDir);
            }

            // save new FS
            if (!newUnManagedFileSystems.isEmpty()) {
                _dbClient.createObject(newUnManagedFileSystems);
            }

            // save old FS
            if (!existingUnManagedFileSystems.isEmpty()) {
                _dbClient.updateObject(existingUnManagedFileSystems);
            }

            _log.info("Discovered {} Isilon file systems.", totalIsilonFSDiscovered);

            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for Isilon: %s; new unmanaged file systems count: %s",
                    storageSystemId.toString(), unmanagedFsCount);
            _log.info(detailedStatusMessage);

        } catch (IsilonException ex) {
            detailedStatusMessage = String.format("Discovery failed for Isilon %s because %s",
                    storageSystemId.toString(), ex.getLocalizedMessage());
            _log.error(detailedStatusMessage, ex);
            throw ex;
        } catch (Exception e) {
            detailedStatusMessage = String.format("Discovery failed for Isilon %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _log.error(detailedStatusMessage, e);
            throw new IsilonCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
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

    private HashMap<String, HashSet<String>> discoverAccessZoneSMBShares(final StorageSystem storageSystem,
            String isilonAccessZone) {
        // Discover All FileShares
        String resumeToken = null;
        HashMap<String, HashSet<String>> allShares = new HashMap<String, HashSet<String>>();
        URI storageSystemId = storageSystem.getId();
        _log.info("discoverAllShares for storage system: {} access zone {} - start", storageSystem, isilonAccessZone);

        try {
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonApi.IsilonList<IsilonSMBShare> isilonShares = isilonApi.listShares(resumeToken, isilonAccessZone);
                List<IsilonSMBShare> isilonSMBShareList = isilonShares.getList();
                HashSet<String> sharesHashSet = null;
                for (IsilonSMBShare share : isilonSMBShareList) {
                    // get the filesystem path and shareid
                    String path = share.getPath();
                    String shareId = share.getId();
                    sharesHashSet = allShares.get(path);
                    // Null means there is no shares at this path in allShares Map. So, creating new entry..
                    if (sharesHashSet == null) {
                        sharesHashSet = new HashSet<>();
                    }
                    sharesHashSet.add(shareId);
                    allShares.put(path, sharesHashSet);
                    _log.info("Discovered SMB Share name {} and path {}", shareId, path);
                }
                resumeToken = isilonShares.getToken();
            } while (resumeToken != null);
            _log.info("discoverd AllShares for access zone {} ", isilonAccessZone);
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

    private <T extends DataObject> void validateSizeLimitAndPersist(List<T> newRecords,
            List<T> oldRecords, int limit) {

        if (newRecords != null && !newRecords.isEmpty() &&
                newRecords.size() >= limit) {
            _partitionManager.insertInBatches(newRecords,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                    UNMANAGED_FILESYSTEM);
            newRecords.clear();
        }

        if (oldRecords != null && !oldRecords.isEmpty() &&
                oldRecords.size() >= limit) {
            _partitionManager.updateInBatches(oldRecords,
                    Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                    UNMANAGED_FILESYSTEM);
            oldRecords.clear();
        }
    }

    private FileShare extractFileShare(String fsNativeId, IsilonSmartQuota quota, StorageSystem storageSystem) {

        _log.debug("extractFileShare for {} and quota {} ", fsNativeId, quota.toString());
        FileShare fs = new FileShare();
        long softLimit = 0;
        int softGrace = 0;
        long notificationLimit = 0;

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
        // TODO Is it a null check??
        if (!quota.getId().equalsIgnoreCase("null")) {
            fs.getExtensions().put(QUOTA, quota.getId());
        }
        if (null != quota.getThresholds()) {
            if (null != quota.getThresholds().getSoft() && capacity != 0) {
                softLimit = quota.getThresholds().getSoft() * 100 / capacity;
            }
            if (null != quota.getThresholds().getSoftGrace() && capacity != 0) {
                softGrace = new Long(quota.getThresholds().getSoftGrace() / (24 * 60 * 60)).intValue();
            }
            if (null != quota.getThresholds().getAdvisory() && capacity != 0) {
                notificationLimit = quota.getThresholds().getAdvisory() * 100 / capacity;
            }
        }
        fs.setSoftLimit(softLimit);
        fs.setSoftGracePeriod(softGrace);
        fs.setNotificationLimit(notificationLimit);

        return fs;
    }

    private UnManagedFileQuotaDirectory getUnManagedFileQuotaDirectory(String fsNativeGuid, IsilonSmartQuota quota,
            StorageSystem storageSystem) {
        String qdNativeId = quota.getPath();
        _log.debug("Converting IsilonSmartQuota {} for fileSystem {}", quota.getPath(), fsNativeGuid);

        int softLimit = 0;
        int softGrace = 0;
        int notificationLimit = 0;
        String nativeGuid = null;

        UnManagedFileQuotaDirectory umfsQd = new UnManagedFileQuotaDirectory();
        StringMap extensionsMap = new StringMap();

        String[] tempDirNames = qdNativeId.split("/");
        umfsQd.setParentFSNativeGuid(fsNativeGuid);

        umfsQd.setLabel(tempDirNames[tempDirNames.length - 1]);

        try {
            nativeGuid = NativeGUIDGenerator.generateNativeGuidForUnManagedQuotaDir(storageSystem.getSystemType(),
                    storageSystem.getSerialNumber(), qdNativeId, "");
        } catch (IOException e) {
            _log.error("Exception while generating NativeGuid for UnManagedQuotaDirectory", e);
        }

        umfsQd.setNativeGuid(nativeGuid);
        umfsQd.setNativeId(qdNativeId);

        long capacity = 0;
        if (quota.getThresholds() != null && quota.getThresholds().getHard() != null) {
            capacity = quota.getThresholds().getHard();
        }

        umfsQd.setSize(capacity);
        if (null != quota.getThresholds().getSoft() && capacity != 0) {
            softLimit = new Long(quota.getThresholds().getSoft() * 100 / capacity).intValue();
        }
        if (null != quota.getThresholds().getSoftGrace() && capacity != 0) {
            softGrace = new Long(quota.getThresholds().getSoftGrace() / (24 * 60 * 60)).intValue();
        }
        if (null != quota.getThresholds().getAdvisory() && capacity != 0) {
            notificationLimit = new Long(quota.getThresholds().getAdvisory() * 100 / capacity).intValue();
        }

        if (null != quota.getId()) {
            extensionsMap.put(QUOTA, quota.getId());
        }

        umfsQd.setSoftLimit(softLimit);
        umfsQd.setSoftGrace(softGrace);
        umfsQd.setNotificationLimit(notificationLimit);
        umfsQd.setExtensions(extensionsMap);

        return umfsQd;
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

    private int isQuotaOrFile(String fsNativeId, int accessZoneDiscPathLength) {

        int pathLength = fsNativeId.split("/").length;

        if (pathLength == (accessZoneDiscPathLength + 1)) {
            return PATH_IS_FILE;
        } else if (pathLength == (accessZoneDiscPathLength + 2)) {
            return PATH_IS_QUOTA;
        }
        return PATH_IS_INVALID;
    }

    private HashMap<String, HashSet<Integer>> discoverAccessZoneExports(StorageSystem storageSystem,
            String isilonAccessZone) throws IsilonCollectionException {

        HashMap<String, HashSet<Integer>> allExports = new HashMap<>();
        URI storageSystemId = storageSystem.getId();
        String resumeToken = null;
        try {
            _log.info("discoverAllExports for storage system {} - start", storageSystemId);
            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            do {
                IsilonApi.IsilonList<IsilonExport> isilonExports = isilonApi.listExports(resumeToken,
                        isilonAccessZone);
                List<IsilonExport> exports = isilonExports.getList();

                for (IsilonExport exp : exports) {
                    _log.info("Discovered fS export {}", exp.toString());
                    if (exp.getPaths() == null || exp.getPaths().isEmpty()) {

                        _log.info("Ignoring export {} as it is not having any path", exp);
                        continue;
                    }
                    // Ignore Export with multiple paths
                    if (exp.getPaths().size() > 1) {
                        _log.info("Discovered Isilon Export: {} has multiple paths so ingnoring it", exp.toString());
                        continue;
                    }
                    String path = exp.getPaths().get(0);
                    HashSet<Integer> exportIds = allExports.get(path);
                    // Null means there is no export at this path in allExport Map. So, creating new entry..
                    if (exportIds == null) {
                        exportIds = new HashSet<>();
                    }
                    exportIds.add(exp.getId());
                    allExports.put(path, exportIds);
                    _log.debug("Discovered fS put export Path {} Export id {}", path, exportIds.size() + ":" + exportIds);
                }
                resumeToken = isilonExports.getToken();
            } while (resumeToken != null);
            _log.info("discoverd All NFS Exports for access zone {} ", isilonAccessZone);
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
    private void setUnmanagedCifsShareACL(UnManagedFileSystem unManagedFileSystem,
            HashSet<String> smbShares,
            List<UnManagedCifsShareACL> unManagedCifsShareACLList,
            StoragePort storagePort,
            String fsname,
            String zoneName, StorageSystem storageSystem,
            IsilonApi isilonApi, List<UnManagedCifsShareACL> oldUnManagedCifsShareACLList) {

        _log.debug("Set CIFS shares and their respective ACL of UMFS: {} from Isilon SMB share details - start", fsname);

        if (null != smbShares && !smbShares.isEmpty()) {
            UnManagedSMBShareMap unManagedSmbShareMap = null;
            if (null == unManagedFileSystem.getUnManagedSmbShareMap()) {
                unManagedSmbShareMap = new UnManagedSMBShareMap();
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
                    unManagedSMBFileShare.setNativeId(shareId);
                    unManagedSMBFileShare.setMountPoint("\\\\" + storagePort.getPortNetworkId() + "\\" + isilonSMBShare.getName());
                    unManagedSMBFileShare.setPath(isilonSMBShare.getPath());
                    unManagedSMBFileShare.setMaxUsers(-1);
                    // setting the dummy permission.This is not used by isilon, but used by other storage system
                    unManagedSMBFileShare.setPermission(FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE);
                    unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);

                    // set Unmanaged SMB Share
                    unManagedSmbShareMap.put(isilonSMBShare.getName(), unManagedSMBFileShare);
                    _log.info("SMB share id {} ", shareId);
                    _log.info("SMB share name {} and fs mount point {} ", unManagedSMBFileShare.getName(),
                            unManagedSMBFileShare.getMountPoint());
                    // process ACL permission
                    UnManagedCifsShareACL unManagedCifsShareACL = null;
                    int aclSize = 0;
                    List<IsilonSMBShare.Permission> permissionList = isilonSMBShare.getPermissions();
                    for (IsilonSMBShare.Permission permission : permissionList) {
                        // Isilon can have deny permission type. Do not ingest the ACL for deny

                        if (FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW
                                .equalsIgnoreCase(permission.getPermissionType())) {

                            aclSize++;
                            _log.debug("IsilonSMBShare: [{}] permission details: {}",
                                    isilonSMBShare.getName(), permission.toString());

                            unManagedCifsShareACL = new UnManagedCifsShareACL();
                            // Set share name
                            unManagedCifsShareACL.setShareName(isilonSMBShare.getName());
                            // Set permission
                            unManagedCifsShareACL.setPermission(permission.getPermission());

                            // We take only username and we can ignore type and id
                            // Set user

                            if (permission.getTrustee() != null) {
                                unManagedCifsShareACL.setUser(permission.getTrustee().getName());
                            } else {
                                _log.warn("No trustee found for permission: {}. so, skipping it.", permission);
                                continue;
                            }
                            // Set filesystem id
                            unManagedCifsShareACL.setFileSystemId(unManagedFileSystem.getId());
                            unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));

                            String fsShareNativeId = unManagedCifsShareACL.getFileSystemShareACLIndex();

                            _log.info("UMFS Share ACL index {}", fsShareNativeId);
                            String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                    .generateNativeGuidForPreExistingFileShare(storageSystem, fsShareNativeId);
                            _log.info("Native GUID {}", fsUnManagedFileShareNativeGuid);

                            // set native guid, so each entry unique
                            unManagedCifsShareACL.setNativeGuid(fsUnManagedFileShareNativeGuid);

                            // Check whether the CIFS share ACL was present in ViPR DB.
                            UnManagedCifsShareACL existingCifsShareACL = checkUnManagedFsCifsACLExistsInDB(_dbClient,
                                    unManagedCifsShareACL.getNativeGuid());
                            if (existingCifsShareACL != null) {
                                // delete the existing acl
                                existingCifsShareACL.setInactive(true);
                                oldUnManagedCifsShareACLList.add(existingCifsShareACL);
                            }
                            unManagedCifsShareACLList.add(unManagedCifsShareACL);
                        }
                    }
                    _log.debug("ACL size of share: [{}] is {}", isilonSMBShare.getName(), aclSize);
                }
            }

            if (!unManagedSmbShareMap.isEmpty()) {
                unManagedFileSystem.setHasShares(true);
            }
        }
    }

    /**
     * Set the unmanaged file system nfs ACl.
     * It set on the file system level and all the exported subdir.
     * 
     * @param unManagedFileSystem
     * @param storageSystem
     * @param isilonApi
     * @param fsNfsACLPaths
     * @param unManagedNfsACLList
     * @param oldunManagedNfsShareACLList
     * @param isilonAccessZoneName
     */
    private void setUnmanagedFileSystemNfsACL(UnManagedFileSystem unManagedFileSystem, StorageSystem storageSystem,
            IsilonApi isilonApi, Set<String> fsNfsACLPaths, List<UnManagedNFSShareACL> unManagedNfsACLList,
            List<UnManagedNFSShareACL> oldunManagedNfsShareACLList, String isilonAccessZoneName) {

        UnManagedNFSShareACL existingNfsACL;
        for (String nfsAclPath : fsNfsACLPaths) {
            _log.info("Start - Setting UnManagedFileSystem NFS ACL for file path {}", nfsAclPath);
            if (nfsAclPath == null || nfsAclPath.isEmpty()) {
                _log.info("NfsACLPaths path is empty");
                continue;
            }

            try {
                IsilonNFSACL isilonNFSAcl = isilonApi.getNFSACL(nfsAclPath);
                for (IsilonNFSACL.Acl tempAcl : isilonNFSAcl.getAcl()) {
                    if (tempAcl.getTrustee() != null) {
                        Persona trustee = tempAcl.getTrustee();
                        UnManagedNFSShareACL unmanagedNFSAcl = new UnManagedNFSShareACL();
                        unmanagedNFSAcl.setFileSystemPath(nfsAclPath);
                        // if name is null ,try to get name by id
                        if (trustee.getName() == null && trustee.getId() != null) {

                            setTrusteeNameUsingSid(isilonApi, trustee, isilonAccessZoneName);
                        }
                        // Verify trustee name
                        // ViPR would manage the ACLs only user/group name
                        // and avoid null pointers too
                        if (trustee.getName() != null) {
                            String[] tempUname = StringUtils.split(trustee.getName(), "\\");

                            if (tempUname.length > 1) {
                                unmanagedNFSAcl.setDomain(tempUname[0]);
                                unmanagedNFSAcl.setUser(tempUname[1]);
                            } else {
                                unmanagedNFSAcl.setUser(tempUname[0]);
                            }

                            unmanagedNFSAcl.setType(trustee.getType());
                            unmanagedNFSAcl.setPermissionType(tempAcl.getAccesstype());
                            unmanagedNFSAcl.setPermissions(StringUtils.join(
                                    getIsilonAccessList(tempAcl.getAccessrights()), ","));

                            unmanagedNFSAcl.setFileSystemId(unManagedFileSystem.getId());
                            unmanagedNFSAcl.setId(URIUtil.createId(UnManagedNFSShareACL.class));

                            _log.info("Unmanaged File System NFS ACL : {}", unmanagedNFSAcl);
                            String fsShareNativeId = unmanagedNFSAcl.getFileSystemNfsACLIndex();
                            _log.info("UMFS NFS ACL index {}", fsShareNativeId);
                            String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                    .generateNativeGuidForPreExistingFileShare(storageSystem, fsShareNativeId);
                            _log.info("Native GUID {}", fsUnManagedFileShareNativeGuid);
                            // set native guid, so each entry unique
                            unmanagedNFSAcl.setNativeGuid(fsUnManagedFileShareNativeGuid);

                            unManagedNfsACLList.add(unmanagedNFSAcl);

                            // Check whether the NFS share ACL was present in ViPR DB.
                            existingNfsACL = checkUnManagedFsNfssACLExistsInDB(_dbClient, unmanagedNFSAcl.getNativeGuid());
                            if (existingNfsACL != null) {
                                // delete the existing acl
                                existingNfsACL.setInactive(true);
                                oldunManagedNfsShareACLList.add(existingNfsACL);
                            }
                        } else {
                            _log.warn("Trustee name is null, and so skipping the File share ACL entry");
                        }
                    }
                    if (unManagedNfsACLList != null && !unManagedNfsACLList.isEmpty()) {
                        unManagedFileSystem.setHasNFSAcl(true);
                    }
                }
            } catch (Exception ex) {
                _log.warn("Unble to access NFS ACLs for path {}", nfsAclPath);
            }
        }
    }

    /**
     * Try populate the missing trustee name from the id and access zone name.
     * 
     * @param isi
     * @param trustee
     * @param zoneName
     */
    private void setTrusteeNameUsingSid(IsilonApi isi, Persona trustee, String zoneName) {
        if ("user".equals(trustee.getType())) {
            // if trustee type is user
            IsilonUser user = isi.getUserDetail(trustee.getId(), zoneName);
            trustee.setName(user.getName());

        } else if ("group".equals(trustee.getType())) {
            // if trustee type is group
            IsilonGroup group = isi.getGroupDetail(trustee.getId(), zoneName);
            trustee.setName(group.getName());
        } else {
            // if trustee type is not available check user and group one by one.
            IsilonUser user = isi.getUserDetail(trustee.getId(), zoneName);
            if (user != null) {
                trustee.setType("user");
                trustee.setName(user.getName());
            } else {
                IsilonGroup group = isi.getGroupDetail(trustee.getId(), zoneName);
                if (group != null) {
                    trustee.setType("group");
                    trustee.setName(group.getName());
                }
            }
        }
    }

    @Override
    public void scan(AccessProfile arg0) throws BaseCollectionException {
    }

    /**
     * If discovery fails, then mark the system as unreachable. The
     * discovery framework will remove the storage system from the database.
     * 
     * @param system
     *            the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.updateObject(system);
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
            unManagedFileSystem.setHasNFSAcl(false);
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
                        .toString(),
                FALSE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(),
                FALSE);

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(), pools);
            // Add support to ingest file systems to thick vpools as well.
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());
            _log.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpools.
                unManagedFileSystem.getSupportedVpoolUris().clear();
                _log.info("No matched vpool found for the file system {}", fileSystem.getNativeId());
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
                        .toString(),
                TRUE);
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

        StringSet softLimit = new StringSet();
        softLimit.add(fileSystem.getSoftLimit().toString());

        StringSet softGrace = new StringSet();
        softGrace.add(fileSystem.getSoftGracePeriod().toString());

        StringSet notificationLimit = new StringSet();
        notificationLimit.add(fileSystem.getNotificationLimit().toString());

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
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.SOFT_LIMIT.toString(), softLimit);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.SOFT_GRACE.toString(), softGrace);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NOTIFICATION_LIMIT.toString(), notificationLimit);

        StringSet provisionedCapacity = new StringSet();
        long capacity = 0;
        if (fileSystem.getCapacity() != null) {
            capacity = fileSystem.getCapacity();
        }
        provisionedCapacity.add(String.valueOf(capacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(),
                provisionedCapacity);

        StringSet allocatedCapacity = new StringSet();
        long usedCapacity = 0;
        if (fileSystem.getUsedCapacity() != null) {
            usedCapacity = fileSystem.getUsedCapacity();
        }
        allocatedCapacity.add(String.valueOf(usedCapacity));
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(),
                allocatedCapacity);

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
            _log.error("Exception while getting SMBShare for {}", shareId, e);
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
     * check QuotaDirectory for given nativeGuid exists in DB
     * 
     * @param nativeGuid
     * @return boolean
     * @throws java.io.IOException
     */
    protected boolean checkStorageQuotaDirectoryExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();

        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getQuotaDirsByNativeGuid(nativeGuid), result);

        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI storageQDURI = iter.next();

            QuotaDirectory quotaDirectory = _dbClient.queryObject(QuotaDirectory.class, storageQDURI);
            if (quotaDirectory != null && !quotaDirectory.getInactive()) {
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
     * check Pre Existing UnManagedFileSystemQuotaDirectory in DB
     * 
     * @param nativeGuid
     * @return UnManagedFileQuotaDirectory
     * @throws IOException
     */
    protected UnManagedFileQuotaDirectory checkUnManagedFileSystemQuotaDirectoryExistsInDB(
            String nativeGuid) throws IOException {
        UnManagedFileQuotaDirectory quotaDirectoryInfo = null;
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getUnManagedFileQuotaDirectoryInfoNativeGUIdConstraint(nativeGuid), result);
        List<URI> umfsQdUris = new ArrayList<URI>();
        Iterator<URI> iter = result.iterator();
        while (iter.hasNext()) {
            URI unFileSystemtURI = iter.next();
            umfsQdUris.add(unFileSystemtURI);
        }

        for (URI umfsQdURI : umfsQdUris) {
            quotaDirectoryInfo = _dbClient.queryObject(UnManagedFileQuotaDirectory.class,
                    umfsQdURI);
            if (quotaDirectoryInfo != null && !quotaDirectoryInfo.getInactive()) {
                return quotaDirectoryInfo;
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

        URIQueryResultList poolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePoolByNativeGuidConstraint(nativeGuid), poolURIs);
        for (URI poolURI : poolURIs) {
            pool = _dbClient.queryObject(StoragePool.class, poolURI);
            if (pool != null && !pool.getInactive()) {
                return pool;
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
     * This function will get the list of access zones present in the system,
     * validate the access zones and return only the valid access zones,
     * so that UMFS discovery would process only objects of required access zones!!
     * 
     * @param storageSystem
     * @param accessZones
     * @return
     */
    private Map<String, NASServer> validateAndGetNASServer(final StorageSystem storageSystem,
            List<IsilonAccessZone> accessZones) {
        NASServer nasServer = null;
        List<IsilonAccessZone> invalidAccessZones = new ArrayList<IsilonAccessZone>();
        Map<String, NASServer> accessZonesMap = new HashMap<String, NASServer>();
        if (accessZones != null && !accessZones.isEmpty()) {
            for (IsilonAccessZone isilonAccessZone : accessZones) {

                if (!isilonAccessZone.isSystem()) {
                    nasServer = findvNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    if (nasServer != null && !nasServer.getInactive()
                            && DiscoveredDataObject.DiscoveryStatus.VISIBLE.name().equals(nasServer.getDiscoveryStatus())) {
                        accessZonesMap.put(isilonAccessZone.getPath() + "/", nasServer);
                    } else {
                        invalidAccessZones.add(isilonAccessZone);
                        _log.info("Nas server {} is not valid, hence filesystem's will not be ingested",
                                isilonAccessZone.getName());
                    }
                } else {
                    nasServer = findPhysicalNasByNativeId(storageSystem, isilonAccessZone.getZone_id().toString());
                    if (nasServer != null && !nasServer.getInactive()) {
                        accessZonesMap.put(isilonAccessZone.getPath() + "/", nasServer);
                    } else {
                        invalidAccessZones.add(isilonAccessZone);
                        _log.info("Nas server {} is not valid, hence filesystem's will not be ingested",
                                isilonAccessZone.getName());
                    }
                }
            }
            // Remove the invalid nas servers, so that we do not discover any object of it!!!
            if (!invalidAccessZones.isEmpty()) {
                accessZones.removeAll(invalidAccessZones);
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
    private void setUnManagedFSExportMap(UnManagedFileSystem umfs, HashMap<String, HashSet<Integer>> expIdMap,
            StoragePort storagePort, String fsPath, String zoneName, IsilonApi isilonApi, StorageSystem storageSystem,
            List<UnManagedFileExportRule> newUnManagedExportRules, List<UnManagedFileExportRule> oldUnManagedExportRules) {

        boolean validExports = false;
        List<UnManagedFileExportRule> exportRules = new ArrayList<>();

        for (Entry<String, HashSet<Integer>> entry : expIdMap.entrySet()) {
            HashSet<Integer> isilonExportIds = entry.getValue();
            _log.info("getting exports for the path {} with id {}", entry.getKey(), entry.getValue());

            List<UnManagedFileExportRule> exportRulesForPath = new ArrayList<>();
            if (isilonExportIds != null && !isilonExportIds.isEmpty()) {
                validExports = getUnManagedFSExportMap(umfs, isilonExportIds,
                        storagePort, fsPath, zoneName, isilonApi, exportRulesForPath);
            }
            if (!validExports) {
                // Clear the export rule list for this path,
                // if there are some invalid export rule at this path
                exportRulesForPath.clear();
            }
            if (!exportRulesForPath.isEmpty()) {
                exportRules.addAll(exportRulesForPath);
                exportRulesForPath.clear();
            }
        }
        UnManagedFileExportRule existingRule = null;
        for (UnManagedFileExportRule dbExportRule : exportRules) {
            _log.info("Un Managed File Export Rule : {}", dbExportRule);
            String fsExportRulenativeId = dbExportRule.getFsExportIndex();
            _log.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
            String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                    .generateNativeGuidForPreExistingFileExportRule(
                            storageSystem, fsExportRulenativeId);
            _log.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);
            dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
            dbExportRule.setId(URIUtil.createId(UnManagedFileExportRule.class));
            existingRule = checkUnManagedFsExportRuleExistsInDB(_dbClient, dbExportRule.getNativeGuid());
            if (null != existingRule) {
                existingRule.setInactive(true);
                oldUnManagedExportRules.add(existingRule);
            }
            newUnManagedExportRules.add(dbExportRule);
        }
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
            StoragePort storagePort, String fsPath, String zoneName, IsilonApi isilonApi,
            List<UnManagedFileExportRule> expRules) {
        UnManagedFSExportMap exportMap = new UnManagedFSExportMap();
        int generatedExportCount = 0;
        ArrayList<IsilonExport> isilonExports = new ArrayList<>();

        if (isilonExportIds != null && isilonExportIds.size() > 1) {
            _log.info("Found multiple export rules for file system path {}, {} ", fsPath, isilonExportIds.size());
        }

        for (Integer expId : isilonExportIds) {
            IsilonExport exp = getIsilonExport(isilonApi, expId, zoneName);
            if (exp == null) {
                _log.info("Ignoring export {} as it is not found", expId);
                continue;
            }
            for (String expPath : exp.getPaths()) {
                if (expPath != null && !expPath.equalsIgnoreCase(fsPath) && !expPath.startsWith(fsPath + "/")) {
                    _log.info("Ignoring export {} as it's path doesn't match with file path {} ", expId, fsPath);
                    continue;
                }
            }
            isilonExports.add(exp);
        }

        StringSet secTypes = new StringSet();
        for (IsilonExport exp : isilonExports) {
            String csSecurityTypes = "";
            Set<String> orderedList = new TreeSet<>();
            // If export has more than one security flavor
            // store all security flavor separated by comma(,)
            for (String sec : exp.getSecurityFlavors()) {
                String securityFlavor = sec;
                // Isilon Maps sys to unix and we do this conversion during export from ViPR
                if (sec.equalsIgnoreCase(UNIXSECURITY)) {
                    securityFlavor = SYSSECURITY;
                }
                orderedList.add(securityFlavor);
            }
            Iterator<String> secIter = orderedList.iterator();
            csSecurityTypes = secIter.next().toString();
            while (secIter.hasNext()) {
                csSecurityTypes += "," + secIter.next().toString();
            }

            if (!csSecurityTypes.isEmpty() && secTypes.contains(csSecurityTypes)) {
                _log.warn("Ignoring file system exports {}, as it contains multiple export rules with same security {}", fsPath,
                        csSecurityTypes);
                return false;
            }
            secTypes.add(csSecurityTypes);

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

            // Create Export rule!!
            UnManagedFileExportRule expRule = new UnManagedFileExportRule();
            expRule.setExportPath(path);
            expRule.setSecFlavor(csSecurityTypes);
            expRule.setAnon(resolvedUser);
            expRule.setDeviceExportId(exp.getId().toString());
            expRule.setFileSystemId(umfs.getId());
            expRule.setMountPoint(storagePort.getPortNetworkId() + ":" + path);

            if (exp != null && exp.getReadOnlyClients() != null && !exp.getReadOnlyClients().isEmpty()) {
                UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                        exp.getReadOnlyClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        csSecurityTypes, RO,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedROFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                generatedExportCount++;

                expRule.setReadOnlyHosts(new StringSet(exp.getReadOnlyClients()));
            }

            if (exp != null && exp.getReadWriteClients() != null && !exp.getReadWriteClients().isEmpty()) {
                UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                        exp.getReadWriteClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        csSecurityTypes, RW,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        exp.getPaths().get(0));
                unManagedRWFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedRWFSExport.getFileExportKey(), unManagedRWFSExport);
                generatedExportCount++;

                expRule.setReadWriteHosts(new StringSet(exp.getReadWriteClients()));
            }

            if (exp != null && exp.getRootClients() != null && !exp.getRootClients().isEmpty()) {
                UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                        exp.getRootClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                        csSecurityTypes, ROOT,
                        resolvedUser, NFS, storagePort.getPortName(), path,
                        path);
                unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                generatedExportCount++;

                expRule.setRootHosts(new StringSet(exp.getRootClients()));
            }

            if (exp.getReadOnlyClients() != null && exp.getReadWriteClients() != null && exp.getRootClients() != null) {
                // Check Clients size
                if (exp.getReadOnlyClients().isEmpty() && exp.getReadWriteClients().isEmpty() && exp.getRootClients().isEmpty()) {
                    // All hosts case. Check whether it is RO/RW/ROOT

                    if (exp.getReadOnly()) {
                        // This is a read only export for all hosts
                        UnManagedFSExport unManagedROFSExport = new UnManagedFSExport(
                                exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                csSecurityTypes, RO,
                                rootUserMapping, NFS, storagePort.getPortName(), path,
                                path);
                        unManagedROFSExport.setIsilonId(exp.getId().toString());
                        exportMap.put(unManagedROFSExport.getFileExportKey(), unManagedROFSExport);
                        generatedExportCount++;

                        // This is a read only export for all hosts
                        expRule.setReadOnlyHosts(new StringSet(exp.getClients()));

                    } else {
                        // Not read Only case
                        if (exp.getMap_all() != null && exp.getMap_all().getUser() != null
                                && exp.getMap_all().getUser().equalsIgnoreCase(ROOT)) {
                            // All hosts with root permission
                            UnManagedFSExport unManagedROOTFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    csSecurityTypes, ROOT,
                                    mapAllUserMapping, NFS, storagePort.getPortName(), path,
                                    path);
                            unManagedROOTFSExport.setIsilonId(exp.getId().toString());
                            exportMap.put(unManagedROOTFSExport.getFileExportKey(), unManagedROOTFSExport);
                            generatedExportCount++;

                            // All hosts with root permission
                            expRule.setRootHosts(new StringSet(exp.getClients()));

                        } else if (exp.getMap_all() != null) {
                            // All hosts with RW permission
                            UnManagedFSExport unManagedRWFSExport = new UnManagedFSExport(
                                    exp.getClients(), storagePort.getPortName(), storagePort.getPortName() + ":" + path,
                                    csSecurityTypes, RW,
                                    rootUserMapping, NFS, storagePort.getPortName(), path,
                                    path);
                            unManagedRWFSExport.setIsilonId(exp.getId().toString());
                            exportMap.put(unManagedRWFSExport.getFileExportKey(), unManagedRWFSExport);
                            generatedExportCount++;

                            // All hosts with RW permission
                            expRule.setReadWriteHosts(new StringSet(exp.getClients()));
                        }
                    }
                }
            }
            // Create Export rule for the export!!!
            expRules.add(expRule);
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

    private HashMap<String, HashSet<Integer>> getExportsIncludingSubDir(String fsPath, HashMap<String, HashSet<Integer>> expMap,
            HashMap<String, HashSet<String>> umfsQuotaMap) {
        HashMap<String, HashSet<Integer>> expMapWithIds = new HashMap<>();

        // Process exports at file system path
        HashSet<Integer> expIds = expMap.get(fsPath);
        if (expIds != null && !expIds.isEmpty()) {
            expMapWithIds.put(fsPath, expIds);
        }

        // Process exports at file quota dir path,this will loop through quotas of file system only..
        HashSet<String> quotasofFS = umfsQuotaMap.get(fsPath);
        if (quotasofFS != null && !quotasofFS.isEmpty()) {
            for (String quotaofFS : quotasofFS) {
                HashSet<Integer> quotaExpIds = expMap.get(quotaofFS);
                if (quotaExpIds != null && !quotaExpIds.isEmpty()) {
                    expMapWithIds.put(quotaofFS, quotaExpIds);
                }
            }
        }
        return expMapWithIds;
    }

    private HashSet<String> getSharesIncludingSubDir(String fsPath, HashMap<String, HashSet<String>> expMap,
            HashMap<String, HashSet<String>> umfsQuotaMap) {
        HashSet<String> shareIDs = new HashSet<>();

        // Process shares at file system path
        HashSet<String> shareIds = expMap.get(fsPath);
        if (shareIds != null && !shareIds.isEmpty()) {
            shareIDs.addAll(shareIds);
        }

        // Process shares at file quota dir path,this will loop through quotas of file system only..
        HashSet<String> quotasofFS = umfsQuotaMap.get(fsPath);
        if (quotasofFS != null && !quotasofFS.isEmpty()) {
            for (String quotaofFS : quotasofFS) {
                HashSet<String> quotaShareIds = expMap.get(quotaofFS);
                if (quotaShareIds != null && !quotaShareIds.isEmpty()) {
                    shareIDs.addAll(quotaShareIds);
                }
            }
        }
        return shareIDs;
    }

    /**
     * convert Isilon's access permissions key set to ViPR's NFS permission set
     * 
     * @param permissions
     * @return
     */
    private ArrayList<String> getIsilonAccessList(ArrayList<String> permissions) {

        ArrayList<String> accessRights = new ArrayList<String>();
        for (String per : permissions) {
            if (per != null) {
                if (per.equalsIgnoreCase(IsilonNFSACL.AccessRights.dir_gen_read.toString())) {
                    accessRights.add("Read");
                }
                if (per.equalsIgnoreCase(IsilonNFSACL.AccessRights.dir_gen_write.toString())) {
                    accessRights.add("write");
                }
                if (per.equalsIgnoreCase(IsilonNFSACL.AccessRights.dir_gen_execute.toString())) {
                    accessRights.add("execute");
                }
                if (per.equalsIgnoreCase(IsilonNFSACL.AccessRights.dir_gen_all.toString())) {
                    accessRights.add("FullControl");
                }
            }
        }
        return accessRights;

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

        Long maxNfsExports = 0L;
        Long maxCifsShares = 30000L;

        if (VersionChecker.verifyVersionDetails(ONEFS_V7_2, system.getFirmwareVersion()) > 0) {
            maxNfsExports = MAX_NFS_EXPORTS_V7_2;
            maxCifsShares = MAX_CIFS_SHARES;
        }

        dbMetrics.put(MetricsKeys.maxNFSExports.name(), String.valueOf(maxNfsExports));
        dbMetrics.put(MetricsKeys.maxCifsShares.name(), String.valueOf(maxCifsShares));

        // set the max capacity in GB
        long maxCapacity = Math.round(getClusterStorageCapacity(system));
        dbMetrics.put(MetricsKeys.maxStorageCapacity.name(), String.valueOf(maxCapacity));
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
        // Get the all storage pools of the storage system
        // compute the cluster capacity based on it's storage pool capacity!!!
        URIQueryResultList storagePoolURIs = new URIQueryResultList();
        _dbClient.queryByConstraint(
                ContainmentConstraint.Factory.getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                storagePoolURIs);
        Iterator<URI> storagePoolIter = storagePoolURIs.iterator();
        while (storagePoolIter.hasNext()) {
            URI storagePoolURI = storagePoolIter.next();
            StoragePool storagePool = _dbClient.queryObject(StoragePool.class,
                    storagePoolURI);
            if (storagePool != null && !storagePool.getInactive()) {
                cluserCap += storagePool.getTotalCapacity();
            }
        }
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
     * Modify Virtual NAS for the specified Isilon cluster storage array
     * 
     * @param system
     *            the StorageSystem object
     * @param isiAccessZone
     *            accessZone object
     * @param vNas
     *            the VirtualNAS object
     * @return VirtualNAS with updated attributes
     */
    private VirtualNAS copyUpdatedPropertiesInVNAS(final StorageSystem system,
            final IsilonAccessZone isiAccessZone, VirtualNAS vNas) {

        vNas.setStorageDeviceURI(system.getId());
        // set name
        vNas.setNasName(isiAccessZone.getName());
        vNas.setNativeId(isiAccessZone.getId());
        // set base directory path
        vNas.setBaseDirPath(isiAccessZone.getPath());
        vNas.setNasState(VirtualNasState.LOADED.toString());

        StringMap dbMetrics = vNas.getMetrics();
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
     * Set the cifs servers for accesszone
     * 
     * @param isiAccessZone
     *            the Isilon access zone object
     * @param nasServer
     *            the NAS server in which CIF server map will be set
     */
    private void setCifsServerMapForNASServer(final IsilonAccessZone isiAccessZone, NASServer nasServer) {

        if (nasServer == null) {
            return;
        }

        _log.info("Set the authentication providers for NAS: {}", nasServer.getNasName());
        String providerName = null;
        String domain = null;
        ArrayList<String> authArrayList = isiAccessZone.getAuth_providers();
        CifsServerMap cifsServersMap = nasServer.getCifsServersMap();
        if (cifsServersMap != null) {
            cifsServersMap.clear();
        } else {
            cifsServersMap = new CifsServerMap();
        }
        if (authArrayList != null && !authArrayList.isEmpty()) {
            for (String authProvider : authArrayList) {
                String[] providerArray = authProvider.split(":");
                providerName = providerArray[0];
                domain = providerArray[1];
                NasCifsServer nasCifsServer = new NasCifsServer();
                nasCifsServer.setName(providerName);
                nasCifsServer.setDomain(domain);
                cifsServersMap.put(providerName, nasCifsServer);
                _log.info("Setting provider: {} and domain: {}", providerName, domain);
            }
        }
        if (isiAccessZone.isAll_auth_providers()) {
            // TODO Possible NPE..
            String[] providerArray = isiAccessZone.getSystem_provider().split(":");
            providerName = providerArray[0];
            domain = providerArray[1];
            NasCifsServer nasCifsServer = new NasCifsServer();
            nasCifsServer.setName(providerName);
            nasCifsServer.setDomain(domain);
            cifsServersMap.put(providerName, nasCifsServer);
            _log.info("Setting provider: {} and domain: {}", providerName, domain);
        }

        nasServer.setCifsServersMap(cifsServersMap);
    }

    /**
     * Find the Virtual NAS by Native ID for Isilon cluster
     * 
     * @param system
     *            storage system information including credentials.
     * @param Native
     *            id of the specified Virtual NAS
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
     * @param system
     *            storage system information including credentials.
     * @param Native
     *            id of the specified Physical NAS
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

    private String getCustomConfigPath() {
        String custompath = "";
        URIQueryResultList results = new URIQueryResultList();

        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getCustomConfigByConfigType(ISILON_PATH_CUSTOMIZATION), results);

        Iterator<URI> iter = results.iterator();

        List<CustomConfig> configList = new ArrayList<>();

        while (iter.hasNext()) {
            CustomConfig config = _dbClient.queryObject(CustomConfig.class, iter.next());
            if (config != null && !config.getInactive()) {
                configList.add(config);
                _log.info("Getting custom Config {}  ", config.getLabel());
            }
        }

        // only 1 config value means only default
        if (configList.size() == 1) {
            custompath = configList.get(0).getValue();
            _log.info("Selecting custom Config {} with value: {} ", configList.get(0).getLabel(), configList.get(0).getValue());
            // More than 1 config means return the custom one, NOT the default one..
        } else {
            for (CustomConfig conf : configList) {
                if (conf.getSystemDefault()) {
                    continue;
                } else {
                    custompath = conf.getValue();
                    _log.info("Selecting custom Config {} with value: {} ", conf.getLabel(), conf.getValue());
                }
            }
        }
        return custompath;
    }

    /**
     * Compute the path length for discovering a file system for a give AccessZone path
     * its
     * CustomConfigPath + 2
     * where path would be like
     * /<access-zone-path>/<vpool_name>/<tenant_name>/<project_name>
     * <access-zone-path> = /ifs/vipr
     */
    private int computeCustomConfigPathLengths(String accessZonePath) {
        String tempCustomConfigPathLength = getCustomConfigPath();
        String initialPath = accessZonePath;
        int discPathLength = 0;
        if (StringUtils.isNotEmpty(tempCustomConfigPathLength)) {
            discPathLength = (initialPath + tempCustomConfigPathLength).split("/").length;
        } else {
            _log.error("CustomConfig path {} has not been set ", tempCustomConfigPathLength);
            discPathLength = (initialPath).split("/").length;
        }

        return discPathLength;
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
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePortByNativeGuidConstraint(portNativeGuid), resultSetList);
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

    private StoragePort getStoragePortFromNasServer(NASServer nasServer) {

        if (nasServer.getStoragePorts() != null && !nasServer.getStoragePorts().isEmpty()) {
            for (String strPort : nasServer.getStoragePorts()) {
                StoragePort sp = _dbClient.queryObject(StoragePort.class, URI.create(strPort));
                if (sp != null) {
                    if (sp.getInactive()
                            || !RegistrationStatus.REGISTERED.toString().equalsIgnoreCase(
                                    sp.getRegistrationStatus())
                            || !DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()
                                    .equals(sp.getCompatibilityStatus())
                            || !DiscoveryStatus.VISIBLE.name().equals(
                                    sp.getDiscoveryStatus())) {
                        continue;
                    }
                    _log.info("found storage port {} for NAS server {} ", sp.getPortName(), nasServer.getNasName());
                    return sp;
                }
            }

        }
        return null;
    }

    private boolean isQuotaUnderAccessZonePath(String fsNativeId, List<String> tempAccessZonePath) {
        for (String accessZonePath : tempAccessZonePath) {
            if (fsNativeId.startsWith(accessZonePath)) {
                return true;
            }
        }
        return false;
    }

    private HashMap<String, Object> discoverAllFileSystem(StorageSystem storageSystem, String resumetoken, String umfsDiscoverPath) {

        URI storageSystemId = storageSystem.getId();
        try {
            _log.info("discoverAllFileSystem for storage system {} - start", storageSystemId);

            IsilonApi isilonApi = getIsilonDevice(storageSystem);
            List<IsilonAccessZone> accessZones = isilonApi.getAccessZones(null);
            List<String> tempAccessZonePath = new ArrayList<>();
            for (IsilonAccessZone accessZone : accessZones) {
                if (!accessZone.isSystem()) {
                    tempAccessZonePath.add(accessZone.getPath() + "/");
                }
            }

            HashSet<String> fsPathSet = new HashSet<>();
            HashSet<String> fsQuotaPathSet = new HashSet<>();
            HashMap<String, IsilonSmartQuota> tempQuotaMap = new HashMap<>();
            IsilonApi.IsilonList<FileShare> isilonFSList = new IsilonApi.IsilonList<>();

            int accessZoneDiscPathLength = computeCustomConfigPathLengths(umfsDiscoverPath);

            IsilonApi.IsilonList<IsilonSmartQuota> quotas = isilonApi.listQuotas(resumetoken, umfsDiscoverPath);
            isilonFSList.setToken(quotas.getToken());

            for (IsilonSmartQuota quota : quotas.getList()) {
                if (quota.getType().compareTo("directory") != 0) {
                    _log.debug("ignore quota path {} with quota id {}:",
                            quota.getPath(), quota.getId() + " and quota type" + quota.getType());
                    continue;
                }
                if ("/ifs/".equals(umfsDiscoverPath) &&
                        isQuotaUnderAccessZonePath(quota.getPath(), tempAccessZonePath)) {
                    continue;
                }

                String fsNativeId = quota.getPath();
                if (isUnderUnmanagedDiscoveryPath(fsNativeId)) {
                    int fsPathType = isQuotaOrFile(fsNativeId, accessZoneDiscPathLength);

                    if (fsPathType == PATH_IS_FILE) {
                        tempQuotaMap.put(quota.getPath(), quota);
                        fsPathSet.add(fsNativeId);
                    }
                    if (fsPathType == PATH_IS_QUOTA) {
                        tempQuotaMap.put(quota.getPath(), quota);
                        fsQuotaPathSet.add(fsNativeId);
                    }
                }
            }

            /*
             * Associate Quota directories with correct File paths
             */
            HashMap<String, Set<String>> fileQuotas = new HashMap<>();
            for (String filePath : fsPathSet) {
                HashSet<String> qdPaths = new HashSet<>();

                for (String qdPath : fsQuotaPathSet) {
                    if (qdPath.startsWith(filePath + "/")) {
                        qdPaths.add(qdPath);
                    }
                }
                if (!qdPaths.isEmpty()) {
                    fsQuotaPathSet.removeAll(qdPaths);
                    fileQuotas.put(filePath, qdPaths);
                }
            }

            HashMap<String, FileShare> fsWithQuotaMap = new HashMap<>();
            HashMap<String, UnManagedFileQuotaDirectory> qdMap = new HashMap<>();

            for (String fsNativeId : fsPathSet) {
                IsilonSmartQuota fileFsQuota = tempQuotaMap.get(fsNativeId);
                FileShare fs = extractFileShare(fsNativeId, fileFsQuota, storageSystem);

                _log.debug("quota id {} with capacity {}", fsNativeId + ":QUOTA:" + fileFsQuota.getId(),
                        fs.getCapacity() + " used capacity " + fs.getUsedCapacity());
                fsWithQuotaMap.put(fsNativeId, fs);

                Set<String> fsQuotaIds = fileQuotas.get(fsNativeId);
                if (null != fsQuotaIds) {
                    for (String quotaNativeId : fsQuotaIds) {
                        IsilonSmartQuota qdQuota = tempQuotaMap.get(quotaNativeId);
                        if (null != qdQuota) {
                            UnManagedFileQuotaDirectory qd = getUnManagedFileQuotaDirectory(fs.getNativeGuid(), qdQuota,
                                    storageSystem);
                            qdMap.put(quotaNativeId, qd);
                        }
                    }
                }
            }

            List<FileShare> discoveredFS = new ArrayList<>();
            discoveredFS.addAll(fsWithQuotaMap.values());
            isilonFSList.addList(discoveredFS);

            List<UnManagedFileQuotaDirectory> discoverdQuotaDirectory = new ArrayList<>();
            discoverdQuotaDirectory.addAll(qdMap.values());

            HashMap<String, Object> discoveredFileDetails = new HashMap<>();
            discoveredFileDetails.put(UMFS_DETAILS, isilonFSList);
            discoveredFileDetails.put(UMFSQD_DETAILS, discoverdQuotaDirectory);
            discoveredFileDetails.put(UMFS_QD_MAP, fileQuotas);

            return discoveredFileDetails;
        } catch (IsilonCollectionException ie) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, ie);
            throw ie;

        } catch (Exception e) {
            _log.error("discoverAllFileSystem failed. Storage system: {}", storageSystemId, e);
            IsilonCollectionException ice = new IsilonCollectionException("discoverAllFileSystem failed. Storage system: "
                    + storageSystemId);
            ice.initCause(e);
            throw ice;
        }
    }

    private IsilonAccessZone getAccessZoneCorresDiscoveryPath(List<IsilonAccessZone> isilonAccessZones, String disPath) {
        String disPathModified = disPath.substring(0, disPath.length() - 1);
        for (IsilonAccessZone az : isilonAccessZones) {
            if (az.getPath().equalsIgnoreCase(disPathModified)) {
                return az;
            }
        }
        return null;
    }
}
