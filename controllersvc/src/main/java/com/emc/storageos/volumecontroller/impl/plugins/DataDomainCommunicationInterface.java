/*
 * Copyright (c) 2015 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.datadomain.restapi.DDOptionInfo;
import com.emc.storageos.datadomain.restapi.DataDomainApiConstants;
import com.emc.storageos.datadomain.restapi.DataDomainClient;
import com.emc.storageos.datadomain.restapi.DataDomainClientFactory;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainApiException;
import com.emc.storageos.datadomain.restapi.errorhandling.DataDomainResourceNotFoundException;
import com.emc.storageos.datadomain.restapi.model.DDExportClient;
import com.emc.storageos.datadomain.restapi.model.DDExportInfo;
import com.emc.storageos.datadomain.restapi.model.DDExportInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDExportList;
import com.emc.storageos.datadomain.restapi.model.DDMCInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfo;
import com.emc.storageos.datadomain.restapi.model.DDMTreeInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDMTreeList;
import com.emc.storageos.datadomain.restapi.model.DDMtreeCapacityInfos;
import com.emc.storageos.datadomain.restapi.model.DDNetworkDetails;
import com.emc.storageos.datadomain.restapi.model.DDNetworkInfo;
import com.emc.storageos.datadomain.restapi.model.DDNetworkList;
import com.emc.storageos.datadomain.restapi.model.DDShareInfo;
import com.emc.storageos.datadomain.restapi.model.DDShareInfoDetail;
import com.emc.storageos.datadomain.restapi.model.DDShareList;
import com.emc.storageos.datadomain.restapi.model.DDStatsCapacityInfo;
import com.emc.storageos.datadomain.restapi.model.DDStatsDataViewQuery;
import com.emc.storageos.datadomain.restapi.model.DDStatsIntervalQuery;
import com.emc.storageos.datadomain.restapi.model.DDSystem;
import com.emc.storageos.datadomain.restapi.model.DDSystemInfo;
import com.emc.storageos.datadomain.restapi.model.DDSystemList;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.ShareACL;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.metering.vnxfile.VNXFileConstants;
import com.emc.storageos.svcs.errorhandling.resources.InternalException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.datadomain.DataDomainStatsRecorder;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileDBInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;

/**
 * Created by zeldib on 2/14/14.
 */
public class DataDomainCommunicationInterface extends ExtendedCommunicationInterfaceImpl {

    private static final String POOL_TYPE = "DataDomainPool";
    private static final String NFS = "NFS";
    private static final String FALSE = "false";
    private static final String TRUE = "true";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;

    private static final String DDMC = "ddmc";
    private static final String DATADOMAIN = "datadomain";
    private static final String MINIMUM_VERSION = "minVersion";
    private static final String CURRENT_VERSION = "currentVersion";

    private Logger _log = LoggerFactory.getLogger(DataDomainCommunicationInterface.class);

    private DataDomainClientFactory _factory;

    /**
     * Set DataDomain API factory
     * 
     * @param ;factory
     */
    public void setDataDomainFactory(DataDomainClientFactory factory) {
        _factory = factory;
    }

    /**
     * Get DataDomain client for the DataDomain provider
     * 
     * @param accessProfile
     *            StorageDevice object
     * @return DataDomainClient object
     * @throws BaseCollectionException
     */
    private DataDomainClient getDataDomainClient(AccessProfile accessProfile) throws BaseCollectionException, DataDomainApiException {

        DataDomainClient ddClient =
                (DataDomainClient) _factory.getRESTClient(
                        DataDomainApiConstants.newDataDomainBaseURI(accessProfile.getIpAddress(),
                                accessProfile.getPortNumber()),
                        accessProfile.getUserName(),
                        accessProfile.getPassword());
        return ddClient;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException, DataDomainApiException {

        long statsCount = 0;
        URI storageSystemId = null;
        StorageSystem storageSystem = null;
        try {
            _log.info("Stats collection for {} using ip {}", accessProfile.getSystemId(),
                    accessProfile.getIpAddress());

            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemId);

            initializeKeyMap(accessProfile);

            DataDomainClient ddClient = getDataDomainClient(accessProfile);
            URI providerId = storageSystem.getActiveProviderURI();
            StorageProvider provider = _dbClient.queryObject(StorageProvider.class, providerId);

            ZeroRecordGenerator zeroRecordGenerator = new FileZeroRecordGenerator();
            CassandraInsertion statsColumnInjector = new FileDBInsertion();
            DataDomainStatsRecorder recorder = new DataDomainStatsRecorder(zeroRecordGenerator, statsColumnInjector);

            // Stats collection start time
            long statsCollectionStartTime = storageSystem.getLastMeteringRunTime();
            // if this is the first time stats collection has been scheduled, we set the
            // start time to the time the storage system was successfully discovered.
            if (statsCollectionStartTime == 0) {
                statsCollectionStartTime = storageSystem.getSuccessDiscoveryTime();
            }
            // Stats collection end time
            long statsCollectionEndTime = accessProfile.getCurrentSampleTime();
            _keyMap.put(Constants._TimeCollected, statsCollectionEndTime);

            // Get list of file systems on the device that are in the DB
            List<URI> fsUris = zeroRecordGenerator.extractVolumesOrFileSharesFromDB(
                    storageSystemId, _dbClient, FileShare.class);

            List<FileShare> fsObjs = _dbClient.queryObject(FileShare.class, fsUris, true);
            // Get capacity usage info on individual mtrees
            List<Stat> stats = new ArrayList<>();

            for (FileShare fileSystem : fsObjs) {
                String fsNativeId = fileSystem.getNativeId();
                String fsNativeGuid = fileSystem.getNativeGuid();

                // Retrieve the last 2 data points only
                int entriesRetrieved = 0;
                List<DDStatsCapacityInfo> statsCapInfos = new ArrayList<>();
                DDStatsIntervalQuery granularity = DDStatsIntervalQuery.hour; // Default
                // Retrieve usage info, one page at a time
                // Retrieve hourly data - lowest resolution supported by DD arrays.
                try {
                    DDMtreeCapacityInfos mtreeCapInfo = ddClient.getMTreeCapacityInfo(storageSystem.getNativeGuid(),
                            fsNativeId, DataDomainApiConstants.STATS_FIRST_PAGE, DataDomainApiConstants.STATS_PAGE_SIZE,
                            DDStatsDataViewQuery.absolute,
                            DDStatsIntervalQuery.hour, true, DataDomainApiConstants.DESCENDING_SORT);
                    entriesRetrieved += mtreeCapInfo.getPagingInfo().getPageEntries();

                    // Collect stats
                    List<DDStatsCapacityInfo> capacityInfos = mtreeCapInfo.getStatsCapacityInfo();
                    if (capacityInfos != null) {
                        statsCapInfos.addAll(capacityInfos);
                    }
                    statsCount += entriesRetrieved;
                } catch (Exception e) {
                    _log.info("Stats collection info not found for fileNativeGuid ", fsNativeGuid);
                    continue;
                }

                // Retrieved all pages, now save in DB if info changed in the latest data point
                long usedCapacity = 0;
                if (fileSystem.getUsedCapacity() != null) {
                    usedCapacity = fileSystem.getUsedCapacity();
                }
                DDStatsCapacityInfo statsCapInfo = null;
                Stat stat = null;

                if (statsCapInfos != null && !statsCapInfos.isEmpty()) {
                    statsCapInfo = statsCapInfos.get(0);
                    _keyMap.put(Constants._Granularity, granularity);
                    stat = recorder.addUsageInfo(statsCapInfo, _keyMap, fsNativeGuid, ddClient);
                }
                // Persist FileShare capacity stats only if usage info has changed
                long allocatedCapacity = 0;
                if (stat != null) {
                    allocatedCapacity = stat.getAllocatedCapacity();
                }

                // TODO: a method to detect changes in stats will be useful
                boolean statsChanged = (usedCapacity != allocatedCapacity) ? true : false;
                if ((stat != null) &&
                        (!fileSystem.getInactive()) &&
                        (statsChanged)) {
                    stats.add(stat);
                    fileSystem.setUsedCapacity(allocatedCapacity);
                    fileSystem.setCapacity(stat.getProvisionedCapacity());
                    _dbClient.persistObject(fileSystem);
                }
            }

            // Determine if a filesystems were deleted from this device and write zero records for deleted ones
            zeroRecordGenerator.identifyRecordstobeZeroed(_keyMap, stats, FileShare.class);
            persistStatsInDB(stats);
            // TODO: Metering task completer will overwrite currTime below with a new
            // time as the last collection time. To avoid this, setLastTime in
            // MeteringTaskCompleter should be modified to set last metering run time
            // only if it
            storageSystem.setLastMeteringRunTime(statsCollectionEndTime);
            _log.info("Done metering device {}, processed {} file system stats ",
                    storageSystemId, statsCount);
            _log.info("End collecting statistics for ip address {}",
                    accessProfile.getIpAddress());
        } catch (Exception e) {
            _log.error("CollectStatisticsInformation failed. Storage system: " +
                    storageSystemId, e);
            throw DataDomainApiException.exceptions.statsCollectionFailed(e.getMessage());
        }

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

    @Override
    public void scan(AccessProfile accessProfile) throws DataDomainApiException {
        DataDomainClient ddClient = getDataDomainClient(accessProfile);
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());
        DDMCInfoDetail ddmcInfo = new DDMCInfoDetail();
        try {
            ddmcInfo = ddClient.getManagementSystemInfo();
        } catch (DataDomainApiException dex) {
            provider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.toString());
            String op = "DDMC info retrieval";
            String sys = provider.getLabel() + "(" + provider.getIPAddress() + ")";
            throw DataDomainApiException.exceptions.opFailedProviderUnreachable(op, sys);
        }
        if (!validDdmcVersion(accessProfile, provider, ddmcInfo)) {
            String version = null;
            String minimumSupportedVersion = null;
            Map<String, String> props = accessProfile.getProps();
            if (props != null) {
                version = props.get(CURRENT_VERSION);
                minimumSupportedVersion = props.get(MINIMUM_VERSION);
            }
            throw DataDomainApiException.exceptions.scanFailedIncompatibleDdmc(
                    version, minimumSupportedVersion);
        }
        Map<String, StorageSystemViewObject> cache = accessProfile.getCache();
        DDSystemList systemList = ddClient.getManagedSystemList();
        for (DDSystemInfo system : systemList.getSystemInfo()) {
            DDSystem ddSystem = ddClient.getDDSystem(system.getId());
            StorageSystemViewObject view = new StorageSystemViewObject();
            view.addprovider(accessProfile.getSystemId().toString());
            view.setDeviceType(accessProfile.getSystemType());
            view.setProperty(StorageSystemViewObject.SERIAL_NUMBER, ddSystem.serialNo);
            view.setProperty(StorageSystemViewObject.MODEL, ddSystem.model);
            view.setProperty(StorageSystemViewObject.STORAGE_NAME, ddSystem.name);
            view.setProperty(StorageSystemViewObject.VERSION, ddSystem.version);
            cache.put(system.getId(), view);
        }
    }

    private boolean validDdmcVersion(AccessProfile accessProfile,
            StorageProvider provider, DDMCInfoDetail ddmcInfo) {

        // Version check
        String version = ddmcInfo.getVersion();
        String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(
                Type.valueOf(DDMC));
        provider.setVersionString(version);
        _log.info("Verifying DDMC version: minimum supported {}, discovered {}",
                minimumSupportedVersion, version);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, version) < 0)
        {
            provider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.toString());
            provider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
            DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, provider.getId());
            _log.error("DDMC version {} is incompatible, minimum supported is {}",
                    version, minimumSupportedVersion);
            Map<String, String> properties = accessProfile.getProps();
            properties.put(MINIMUM_VERSION, minimumSupportedVersion);
            properties.put(CURRENT_VERSION, version);
            return false;
        }
        provider.setConnectionStatus(ConnectionStatus.CONNECTED.toString());
        provider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());

        _dbClient.persistObject(provider);
        return true;
    }

    private boolean validDdosVersion(StorageSystem storageSystem, DataDomainClient ddClient) {
        String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(
                Type.valueOf(DATADOMAIN));
        DDSystem ddSystem = ddClient.getDDSystem(storageSystem.getNativeGuid());
        _log.info("Verifying DDOS version: minimum supported {}, discovered {}",
                minimumSupportedVersion, ddSystem.version);
        if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, ddSystem.version) < 0)
        {
            return false;
        }
        return true;
    }

    @Override
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException {
        DataDomainClient ddClient = getDataDomainClient(accessProfile);
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, accessProfile.getSystemId());
        URI providerId = storageSystem.getActiveProviderURI();
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, providerId);
        if (storageSystem == null || storageSystem.getInactive()) {
            return;
        }
        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                                .toString()))) {

            discoverUnManagedFileSystems(ddClient, storageSystem);
            discoverUnManagedNewExports(ddClient, storageSystem);
            discoverUnManagedCifsShares(ddClient, storageSystem);
        } else {
            discoverAll(ddClient, storageSystem);
        }

    }

    public void discoverAll(DataDomainClient ddClient, StorageSystem storageSystem) throws DataDomainApiException {

        String detailedStatusMessage = "";
        try {

            if (!validDdosVersion(storageSystem, ddClient)) {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
            } else {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                storageSystem.setReachableStatus(true);
            }

            discoverPool(ddClient, storageSystem);
            discoverPort(ddClient, storageSystem);
            discoverMtrees(ddClient, storageSystem);

            _dbClient.persistObject(storageSystem);
            detailedStatusMessage = "Completed discovery of the storageSystem " + storageSystem.getId().toString();

        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format("Discovery failed for DataDomain %s because %s",
                    storageSystem.getId().toString(), e.getLocalizedMessage());
            _log.error(detailedStatusMessage, e);
            throw DataDomainApiException.exceptions.failedDataDomainDiscover(storageSystem.getId().toString(), e);
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

    private void discoverPool(DataDomainClient ddClient, StorageSystem storageSystem) {

        boolean newPool = false;
        boolean match = false;
        StoragePool storagePool = getPoolFromDB(storageSystem);

        if (storagePool == null)
        {
            // New storage pool
            storagePool = new StoragePool();
            storagePool.setId(URIUtil.createId(StoragePool.class));
            String nativeGid = NativeGUIDGenerator.generateNativeGuid(
                    storageSystem, storageSystem.getNativeGuid(), NativeGUIDGenerator.POOL);
            storagePool.setNativeGuid(nativeGid);
            storagePool.setLabel(storageSystem.getLabel());
            storagePool.setPoolName(storageSystem.getLabel());
            storagePool.setPoolClassName(POOL_TYPE);
            storagePool.setPoolServiceType(StoragePool.PoolServiceType.file.toString());
            storagePool.setStorageDevice(storageSystem.getId());

            StringSet protocols = new StringSet();
            protocols.add(StorageProtocol.File.NFS.name());
            protocols.add(StorageProtocol.File.CIFS.name());
            storagePool.setProtocols(protocols);
            storagePool.setLongTermRetention(true);
            storagePool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_ONLY.toString());
            storagePool.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
            newPool = true;
            _log.info("Creating new storage pool for system : {} ", storageSystem.getNativeGuid());
        }
        storagePool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.toString());
        storagePool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
        DDSystem ddSystem = ddClient.getDDSystem(storageSystem.getNativeGuid());
        storagePool.setNativeId(ddSystem.id);
        storagePool.setTotalCapacity(ddSystem.logicalCapacity.getTotal() >> DataDomainApiConstants.B_TO_KB_SHIFT);
        storagePool.setFreeCapacity(ddSystem.logicalCapacity.getAvailable() >> DataDomainApiConstants.B_TO_KB_SHIFT);
        storagePool.setSubscribedCapacity(ddSystem.logicalCapacity.getUsed() >> DataDomainApiConstants.B_TO_KB_SHIFT);

        StringMap capacityProp = storagePool.getCustomProperties();
        capacityProp.put(DataDomainApiConstants.TOTAL_PHYSICAL_CAPACITY,
                Long.valueOf(ddSystem.physicalCapacity.getTotal() >> DataDomainApiConstants.B_TO_KB_SHIFT).toString());
        capacityProp.put(DataDomainApiConstants.AVAILABLE_PHYSICAL_CAPACITY,
                Long.valueOf(ddSystem.physicalCapacity.getAvailable() >> DataDomainApiConstants.B_TO_KB_SHIFT).toString());
        capacityProp.put(DataDomainApiConstants.USED_PHYSICAL_CAPACITY,
                Long.valueOf(ddSystem.physicalCapacity.getUsed() >> DataDomainApiConstants.B_TO_KB_SHIFT).toString());
        capacityProp.put(DataDomainApiConstants.SYSTEM_QUOTA,
                Long.valueOf(ddSystem.subscribedCapacity >> DataDomainApiConstants.B_TO_KB_SHIFT).toString());
        capacityProp.put(DataDomainApiConstants.COMPRESSION_FACTOR,
                Double.valueOf(ddSystem.compressionFactor).toString());
        DDMTreeList list = ddClient.getMTreeList(storageSystem.getNativeGuid());
        capacityProp.put(DataDomainApiConstants.NUMBER_MTREES,
                Long.valueOf(list.mtree.size()).toString());

        // Temporarily fix until DD fixes logical capacity computation
        if (ddSystem.compressionFactor < 0.5) {
            capacityProp.put(DataDomainApiConstants.COMPRESSION_FACTOR, "1.0");
            storagePool.setTotalCapacity(Long.valueOf(capacityProp.get(DataDomainApiConstants.TOTAL_PHYSICAL_CAPACITY)));
            storagePool.setFreeCapacity(Long.valueOf(capacityProp.get(DataDomainApiConstants.AVAILABLE_PHYSICAL_CAPACITY)));
            storagePool.setSubscribedCapacity(Long.valueOf(capacityProp.get(DataDomainApiConstants.USED_PHYSICAL_CAPACITY)));
        }

        if ((DiscoveredDataObject.DataCollectionJobStatus.ERROR.name()
                .equals(storageSystem.getDiscoveryStatus()))
                || (DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name()
                        .equals(storageSystem.getCompatibilityStatus()))) {
            storagePool.setDiscoveryStatus(DiscoveryStatus.NOTVISIBLE.name());
            storagePool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
        } else {
            storagePool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            storagePool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        }

        if (ImplicitPoolMatcher.checkPoolPropertiesChanged(storagePool.getCompatibilityStatus(),
                DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())) {
            match = true;
        }

        if (newPool) {
            _dbClient.createObject(storagePool);
        }
        else {
            _dbClient.persistObject(storagePool);
        }

        if (match) {
            StringBuffer errorMessage = new StringBuffer();
            ImplicitPoolMatcher.matchModifiedStoragePoolsWithAllVpool(Arrays.asList(storagePool),
                    _dbClient, _coordinator,
                    storageSystem.getId(), errorMessage);
        }

        _log.info("discoverPools for storage system {} - complete", storageSystem.getId());
    }

    private void discoverPort(DataDomainClient ddClient, StorageSystem storageSystem) {

        boolean newPort = false;

        _log.info("discoverPorts for storage system {} - start", storageSystem.getId());
        try {
            DDNetworkList networks = ddClient.getNetworks(storageSystem.getNativeGuid());
            if (networks.network.isEmpty()) {
                return;
            }
            List<StoragePort> ports = new ArrayList<StoragePort>();
            for (DDNetworkInfo network : networks.network) {
                DDNetworkDetails detailedNetworkInfo = ddClient.getNetwork(storageSystem.getNativeGuid(), network.getId());
                if ((!detailedNetworkInfo.getEnabled()) || (detailedNetworkInfo.getIp() == null)) {
                    continue;
                }
                StoragePort storagePort = null;
                // Check if storage port was already discovered
                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, detailedNetworkInfo.getIp(),
                        NativeGUIDGenerator.PORT);
                URIQueryResultList results = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                        getStoragePortByNativeGuidConstraint(portNativeGuid), results);

                while (results.iterator().hasNext()) {
                    StoragePort port = _dbClient.queryObject(StoragePort.class, results.iterator().next());
                    if (!port.getInactive() && port.getStorageDevice().equals(storageSystem.getId())) {
                        storagePort = port;
                        break;
                    }
                }

                if (storagePort == null) {
                    // Create DataDomain storage port for IP address
                    storagePort = new StoragePort();
                    storagePort.setId(URIUtil.createId(StoragePort.class));
                    storagePort.setTransportType("IP");
                    storagePort.setNativeGuid(portNativeGuid);
                    storagePort.setLabel(portNativeGuid);
                    storagePort.setStorageDevice(storageSystem.getId());
                    storagePort.setPortName(detailedNetworkInfo.getName());
                    storagePort.setPortGroup(detailedNetworkInfo.getName());
                    storagePort.setPortNetworkId(detailedNetworkInfo.getIp());
                    storagePort.setIpAddress(detailedNetworkInfo.getIp());
                    storagePort.setPortSpeed((long) detailedNetworkInfo.getLinkSpeed());

                    storagePort.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
                    newPort = true;
                    _log.info("Creating new storage port using NativeGuid : {}", portNativeGuid);
                }
                storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                storagePort.setOperationalStatus(StoragePort.OperationalStatus.OK.toString());
                storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                _log.info("discoverPorts for storage system {} - complete", storageSystem.getId());
                StoragePortAssociationHelper.updatePortAssociations(Arrays.asList(storagePort), _dbClient);
                if (newPort) {
                    _dbClient.createObject(storagePort);
                }
                else {
                    _dbClient.persistObject(storagePort);
                }
                ports.add(storagePort);
            }
            DiscoveryUtils.checkStoragePortsNotVisible(ports, _dbClient, storageSystem.getId());
        } catch (InternalException e) {
            throw e;
        } catch (Exception e) {
            _log.error("discoverPorts failed. Storage system: " + storageSystem.getId(), e);
            throw DataDomainApiException.exceptions.failedDataDomainDiscover(storageSystem.getNativeGuid(), e);
        }
    }

    private void discoverMtrees(DataDomainClient ddClient, StorageSystem storageSystem) {

        URIQueryResultList mtreeList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getStorageDeviceFileshareConstraint(storageSystem.getId()), mtreeList);
        Iterator<URI> mtreeItr = mtreeList.iterator();
        while (mtreeItr.hasNext()) {
            FileShare mtree = _dbClient.queryObject(FileShare.class, mtreeItr.next());
            if (!mtree.getInactive()) {
                try {
                    DDMTreeInfoDetail mtreeInfo = ddClient.getMTree(storageSystem.getNativeGuid(), mtree.getNativeId());
                    if (mtreeInfo.quotaConfig != null) {
                        // it should be always true.
                        // We do not inject resources without quota
                        mtree.setCapacity(mtreeInfo.quotaConfig.getHardLimit());
                    }
                    mtree.setUsedCapacity(mtreeInfo.logicalCapacity.getUsed());
                } catch (DataDomainResourceNotFoundException ex) {
                    mtree.setCapacity(0L);
                } catch (DataDomainApiException dex) {
                    mtree.setCapacity(0L);
                }
            }
        }
    }

    private void discoverUnManagedFileSystems(DataDomainClient ddClient, StorageSystem storageSystem) throws DataDomainApiException {

        String detailedStatusMessage = "Discovery of DataDomain Unmanaged FileSystem started";

        List<UnManagedFileSystem> newUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();
        StoragePool pool = getPoolFromDB(storageSystem);
        StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());

        DDMTreeInfoDetail mtree = null;
        try {
            DDMTreeList mtreeList = ddClient.getMTreeList(storageSystem.getNativeGuid());
            for (DDMTreeInfo mtreeInfo : mtreeList.mtree) {
                mtree = ddClient.getMTree(storageSystem.getNativeGuid(), mtreeInfo.getId());
                if (mtree == null || mtree.delStatus == DataDomainApiConstants.FILE_DELETED) {
                    continue;
                }
                // Filtering mtrees that are not supporting either of NFS & CIFS
                if ((mtree.protocolName == null) || (mtree.protocolName.isEmpty())) {
                    _log.info("Mtree: {} doesn't contain any protocol defined so ignoring it", mtree.name);
                    continue;
                }
                else {
                    if ((mtree.protocolName.contains(DataDomainApiConstants.NFS_PROTOCOL))
                            || (mtree.protocolName.contains(DataDomainApiConstants.CIFS_PROTOCOL))) {
                        _log.info("Mtree: {} contains supported protocol:{} so discovering it", mtree.name, mtree.protocolName.toArray());
                    }
                    else {
                        _log.info("Mtree: {} contains unsupported protocol:{} so ignoring it", mtree.name, mtree.protocolName.toArray());
                        continue;
                    }
                }

                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(),
                        storageSystem.getSerialNumber().toUpperCase(),
                        mtreeInfo.getId());

                // If the filesystem already exists in db..just continue.
                // No Need to create an UnManaged Filesystems.
                URIQueryResultList result = new URIQueryResultList();
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getFileSystemNativeGUIdConstraint(fsNativeGuid), result);
                if (result.iterator().hasNext()) {
                    continue;
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.
                        generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                storageSystem.getSerialNumber().toUpperCase(),
                                mtreeInfo.getId());

                UnManagedFileSystem unManagedFS = getUnManagedFileSystemFromDB(fsUnManagedFsNativeGuid);
                boolean alreadyExist = unManagedFS == null ? false : true;
                unManagedFS = createUnManagedFileSystem(unManagedFS, fsUnManagedFsNativeGuid, mtree,
                        storageSystem, pool, matchedVPools);
                if (alreadyExist) {
                    existingUnManagedFileSystems.add(unManagedFS);
                } else {
                    newUnManagedFileSystems.add(unManagedFS);
                }
                allDiscoveredUnManagedFileSystems.add(unManagedFS.getId());
            }

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);

            if (newUnManagedFileSystems != null && !newUnManagedFileSystems.isEmpty()) {
                // Add UnManagedFileSystem
                _dbClient.createObject(newUnManagedFileSystems);
                _log.info("{} {} Records inserted to DB", newUnManagedFileSystems.size(), UNMANAGED_FILESYSTEM);

            }

            if (existingUnManagedFileSystems != null && !existingUnManagedFileSystems.isEmpty()) {
                _dbClient.updateAndReindexObject(existingUnManagedFileSystems);
                _log.info("{} {} Records updated to DB", existingUnManagedFileSystems.size(), UNMANAGED_FILESYSTEM);
            }
            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE.toString());
            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for DataDomain system: %s",
                    storageSystem.getId().toString());

        } catch (DataDomainApiException dde) {
            detailedStatusMessage = "DiscoverStorage failed" + dde.getMessage();
            _log.error("discoverStorage failed.  Storage system: " + storageSystem.getId().toString());
            throw dde;
        } catch (Exception e) {
            detailedStatusMessage = "DiscoverStorage failed" + e.getMessage();
            _log.error("discoverStorage failed. Storage system: " + storageSystem.getId().toString(), e);
            throw DataDomainApiException.exceptions.failedDataDomainDiscover(storageSystem.getNativeGuid(), e);
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

    private void discoverUnManagedExports(DataDomainClient ddClient, StorageSystem storageSystem) throws DataDomainApiException {

        Map<String, UnManagedFileSystem> existingUnManagedFileSystems = new HashMap<String, UnManagedFileSystem>();

        try {

            List<StoragePort> ports = getPortFromDB(storageSystem);

            // Get exports on the array and loop through each export.
            DDExportList exportList = ddClient.getExports(storageSystem.getNativeGuid());
            for (DDExportInfo exp : exportList.getExports()) {
                DDExportInfoDetail export = ddClient.getExport(storageSystem.getNativeGuid(), exp.getId());
                if (export.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                    continue;
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.
                        generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                storageSystem.getSerialNumber().toUpperCase(),
                                export.getMtreeID());

                UnManagedFileSystem unManagedFS = existingUnManagedFileSystems.get(fsUnManagedFsNativeGuid);
                if (unManagedFS == null) {
                    unManagedFS = getUnManagedFileSystemFromDB(fsUnManagedFsNativeGuid);
                }
                if (unManagedFS != null) {
                    createExportMap(export, unManagedFS, ports);
                    existingUnManagedFileSystems.put(fsUnManagedFsNativeGuid, unManagedFS);
                    // Adding this additional logic to avoid OOM
                    if (existingUnManagedFileSystems.size() == MAX_UMFS_RECORD_SIZE) {
                        _dbClient.persistObject(new ArrayList<UnManagedFileSystem>(existingUnManagedFileSystems.values()));
                        existingUnManagedFileSystems.clear();
                    }

                } else {
                    _log.info("FileSystem " + fsUnManagedFsNativeGuid + " is not present in ViPR DB. Hence ignoring " + export + " export");
                }
            }

            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.persistObject(new ArrayList<UnManagedFileSystem>(existingUnManagedFileSystems.values()));
                _log.info("{} {} Records updated to DB", existingUnManagedFileSystems.size(), UNMANAGED_FILESYSTEM);
            }
            storageSystem
                    .setLastDiscoveryStatusMessage("");
            _dbClient.persistObject(storageSystem);
        } catch (DataDomainApiException dde) {
            _log.error("discoverStorage failed.  Storage system: " + storageSystem.getId().toString());
            throw dde;
        } catch (Exception e) {
            _log.error("discoverStorage failed. Storage system: " + storageSystem.getId().toString(), e);
            DataDomainApiException.exceptions.failedDataDomainDiscover(storageSystem.getNativeGuid(), e);
        }
    }

    private void createExportMap(DDExportInfoDetail export, UnManagedFileSystem unManagedFS, List<StoragePort> ports) {

        Map<String, List<String>> endPointMap = new HashMap<String, List<String>>();

        for (DDExportClient client : export.getClients()) {
            List<String> clients = endPointMap.get(client.getOptions());
            if (clients == null) {
                clients = new ArrayList<String>();
                endPointMap.put(client.getOptions(), clients);
            }
            clients.add(client.getName());
        }

        UnManagedFSExportMap exportMap = unManagedFS.getFsUnManagedExportMap();
        if (exportMap == null) {
            exportMap = new UnManagedFSExportMap();
            unManagedFS.setFsUnManagedExportMap(exportMap);
        }
        Set<String> options = endPointMap.keySet();
        for (String option : options) {
            UnManagedFSExport fExport = new UnManagedFSExport();
            fExport.setNativeId(export.getId());
            fExport.setMountPath(export.getPath());
            fExport.setMountPoint(export.getPath());
            fExport.setPath(export.getPath());
            fExport.setClients(endPointMap.get(option));
            DDOptionInfo optionInfo = DDOptionInfo.parseOptions(option);
            fExport.setPermissions(optionInfo.permission);
            fExport.setProtocol(NFS);
            fExport.setRootUserMapping(optionInfo.rootMapping);
            fExport.setSecurityType(optionInfo.security);
            // need to find the port which was used to create this export.
            // Right now DD API does not contain any info on that.
            Collections.shuffle(ports);
            fExport.setStoragePort(ports.get(0).getId().toString());
            fExport.setStoragePortName(ports.get(0).getPortName());
            String exportKey = fExport.getFileExportKey();
            exportMap.put(exportKey, fExport);
        }
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
            system.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
            _dbClient.persistObject(system);
        } catch (DatabaseException e) {
            _log.error("discoverStorage failed.  Failed to update discovery status to ERROR.", e);
        }
    }

    private StoragePool getPoolFromDB(StorageSystem system) {

        String nativeGid = NativeGUIDGenerator.generateNativeGuid(
                system, system.getNativeGuid(), NativeGUIDGenerator.POOL);

        URIQueryResultList storagePoolList = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                getStoragePoolByNativeGuidConstraint(nativeGid), storagePoolList);
        Iterator<URI> poolItr = storagePoolList.iterator();
        while (poolItr.hasNext()) {
            StoragePool pool = _dbClient.queryObject(StoragePool.class, poolItr.next());
            if (pool.getStorageDevice().equals(system.getId())) {
                return pool;
            }
        }
        return null;
    }

    private List<StoragePort> getPortFromDB(StorageSystem system) {

        List<StoragePort> ports = new ArrayList<StoragePort>();
        URIQueryResultList storagePortList = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.
                getStorageDeviceStoragePortConstraint(system.getId()), storagePortList);
        Iterator<URI> portItr = storagePortList.iterator();
        while (portItr.hasNext()) {
            StoragePort port = _dbClient.queryObject(StoragePort.class, portItr.next());
            if (!port.getInactive()) {
                ports.add(port);
            }
        }
        return ports;
    }

    /**
     * Retrieve the FS from DB if it exists already
     * 
     * @param nativeGuid
     * @return unManageFileSystem
     * @throws IOException
     */
    protected UnManagedFileSystem getUnManagedFileSystemFromDB(
            String nativeGuid) {
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

        if (!filesystemUris.isEmpty()) {
            filesystemInfo = _dbClient.queryObject(UnManagedFileSystem.class,
                    filesystemUris.get(0));
        }
        return filesystemInfo;

    }

    private UnManagedFileSystem createUnManagedFileSystem(UnManagedFileSystem unManagedFileSystem,
            String unManagedFileSystemNativeGuid,
            DDMTreeInfoDetail mtree,
            StorageSystem system,
            StoragePool pool,
            StringSet vPools) {
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil.createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setFsUnManagedExportMap(new UnManagedFSExportMap());
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
        }
        else { // existing File System
            UnManagedFSExportMap exportMap = unManagedFileSystem.getFsUnManagedExportMap();
            if (exportMap != null) {
                exportMap.clear();
            }
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        // TODO: DD does not provide snapshot API yet
        // This will be determined at snapshot discovery, once implemented
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        // DD supports only thinly provisioned FS
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                        .toString(), TRUE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(), TRUE);

        // Don't yet know if the FS is exported, to be determined at export discovery
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(), FALSE);
        unManagedFileSystem.setHasExports(false);

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            unManagedFileSystem.setStoragePoolUri(pool.getId());
        }
        _log.debug("Matched Pools : {}", Joiner.on("\t").join(vPools));

        if (null == vPools || vPools.isEmpty()) {
            unManagedFileSystem.getSupportedVpoolUris().clear();
        } else {
            unManagedFileSystem.getSupportedVpoolUris().replace(vPools);
            _log.info("Replaced Pools :" + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
        }

        List<StoragePort> ports = getPortFromDB(system);
        if (ports != null) {
            StringSet storagePorts = new StringSet();
            for (StoragePort storagePort : ports) {
                String portId = storagePort.getId().toString();
                storagePorts.add(portId);
            }
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(),
                    storagePorts);
        }

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        StringSet provisionedCapacity = new StringSet();
        if (mtree.quotaConfig != null) {
            provisionedCapacity.add(Long.toString(mtree.quotaConfig.getHardLimit()));
        } else {
            provisionedCapacity.add(Long.toString(mtree.logicalCapacity.getTotal()));
        }
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PROVISIONED_CAPACITY
                        .toString(), provisionedCapacity);
        StringSet allocatedCapacity = new StringSet();
        if (mtree.logicalCapacity != null) {
            allocatedCapacity.add(Long.toString(mtree.logicalCapacity.getUsed()));
        } else {
            allocatedCapacity.add(Long.toString(0));
        }
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.ALLOCATED_CAPACITY
                        .toString(), allocatedCapacity);

        // Save off FileSystem Name, Path, Mount and label information

        String name = mtree.name;
        StringSet fsName = new StringSet();
        fsName.add(name);
        StringSet fsMountPath = new StringSet();
        fsMountPath.add(mtree.name);
        StringSet nativeId = new StringSet();
        nativeId.add(mtree.id);

        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NAME.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.NATIVE_ID.toString(), nativeId);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.PATH.toString(), fsName);
        unManagedFileSystemInformation.put(
                UnManagedFileSystem.SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .setFileSystemCharacterstics(unManagedFileSystemCharacteristics);
        return unManagedFileSystem;
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

    private void discoverUnManagedNewExports(DataDomainClient ddClient, StorageSystem storageSystem) throws DataDomainApiException {

        storageSystem
                .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                        .toString());
        String detailedStatusMessage = "Discovery of Data Domain Unmanaged Exports started";
        storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);

        // Used to cache UMFS once retrieved from DB
        Map<String, UnManagedFileSystem> existingUnManagedFileSystems = new HashMap<String, UnManagedFileSystem>();

        // Used to Save the rules to DB
        List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();

        try {

            // Get exports on the array and loop through each export.
            DDExportList exportList = ddClient.getExports(storageSystem.getNativeGuid());

            // Verification Utility
            UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
                    _dbClient);

            for (DDExportInfo exp : exportList.getExports()) {

                DDExportInfoDetail export = ddClient.getExport(storageSystem.getNativeGuid(), exp.getId());
                if (export.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                    continue;
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.
                        generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                storageSystem.getSerialNumber().toUpperCase(),
                                export.getMtreeID());

                // Get UMFS from cache if possible, otherwise try to retrieve from DB
                UnManagedFileSystem unManagedFS = existingUnManagedFileSystems.get(fsUnManagedFsNativeGuid);
                if (unManagedFS == null) {
                    unManagedFS = getUnManagedFileSystemFromDB(fsUnManagedFsNativeGuid);
                }

                // Used for rules validation
                List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();
                if (unManagedFS != null) {
                    // Add UMFS to cache
                    existingUnManagedFileSystems.put(fsUnManagedFsNativeGuid, unManagedFS);

                    // Build ViPR export rules from the export retrieved from the array
                    List<UnManagedFileExportRule> exportRules = applyAllSecurityRules(export, unManagedFS.getId());
                    _log.info("Number of exports discovered for file system {} is {}", unManagedFS.getId(), exportRules.size());

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
                        // Build all export rules list.
                        unManagedExportRules.add(dbExportRule);
                    }

                    // Validate Rules Compatible with ViPR - Same rules should
                    // apply as per API SVC Validations.
                    if (!unManagedExportRules.isEmpty()) {
                        boolean isAllRulesValid = validationUtility
                                .validateUnManagedExportRules(unManagedExportRules, false);
                        if (isAllRulesValid) {
                            _log.info("Validating rules success for export {}", export.getPath());
                            for (UnManagedFileExportRule exportRule : unManagedExportRules) {
                                UnManagedFileExportRule existingRule = checkUnManagedFsExportRuleExistsInDB(_dbClient,
                                        exportRule.getNativeGuid());
                                if (existingRule == null) {
                                    newUnManagedExportRules.add(exportRule);
                                } else {
                                    // Remove the existing rule.
                                    existingRule.setInactive(true);
                                    _dbClient.persistObject(existingRule);
                                    newUnManagedExportRules.add(exportRule);
                                }
                            }
                            unManagedFS.setHasExports(true);
                            unManagedFS.putFileSystemCharacterstics(
                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                            .toString(), TRUE);
                            _dbClient.persistObject(unManagedFS);
                            _log.info("File System {} has Exports and their size is {}", unManagedFS.getId(),
                                    newUnManagedExportRules.size());
                        } else {
                            _log.warn("Validating rules failed for export {}. Ignroing to import these rules into ViPR DB",
                                    export.getPath());
                            // Don't consider the file system with invalid exports!!!
                            unManagedFS.setInactive(true);
                        }
                        
                    }

                    // Adding this additional logic to avoid OOM
                    if (newUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                        _log.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                        _dbClient.persistObject(newUnManagedExportRules);
                        newUnManagedExportRules.clear();
                    }

                } else {
                    _log.info("FileSystem " + fsUnManagedFsNativeGuid +
                            " is not present in ViPR DB. Hence ignoring " + export + " export");
                }
            }
            if (!newUnManagedExportRules.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.persistObject(newUnManagedExportRules);
                _log.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
            }
            
            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _dbClient.persistObject(existingUnManagedFileSystems.values());
                _log.info("{} {} Records updated to DB", existingUnManagedFileSystems.size(), UNMANAGED_FILESYSTEM);
            }

            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                    .toString());

            // discovery succeeded
            detailedStatusMessage = String.format("Discovery completed successfully for Data Domain: %s",
                    storageSystem.getId().toString());
            storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);

        } catch (DataDomainApiException dde) {
            _log.error("discoverStorage failed.  Storage system: "
                    + storageSystem.getId());
        } catch (Exception e) {
            _log.error("discoverStorage failed. Storage system: "
                    + storageSystem.getId(), e);
        } finally {
            if (storageSystem != null) {
                try {
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    private void discoverUnManagedCifsShares(DataDomainClient ddClient, StorageSystem storageSystem) throws DataDomainApiException {

        storageSystem
                .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                        .toString());
        String detailedStatusMessage = "Discovery of Data Domain Unmanaged Cifs Shares started";
        storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);

        // Used to cache UMFS once retrieved from DB
        Map<String, UnManagedFileSystem> existingUnManagedFileSystems = new HashMap<String, UnManagedFileSystem>();

        // Used to Save the CIFS ACLs to DB
        List<UnManagedCifsShareACL> newUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();
        List<UnManagedCifsShareACL> oldUnManagedCifsACLs = new ArrayList<UnManagedCifsShareACL>();

        try {

            // Get exports on the array and loop through each export.
            DDShareList shareList = ddClient.getShares(storageSystem.getNativeGuid());

            for (DDShareInfo shareInfo : shareList.getShares()) {

                DDShareInfoDetail share = ddClient.getShare(storageSystem.getNativeGuid(), shareInfo.getId());
                if (share.getPathStatus() != DataDomainApiConstants.PATH_EXISTS) {
                    continue;
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.
                        generateNativeGuidForPreExistingFileSystem(storageSystem.getSystemType(),
                                storageSystem.getSerialNumber().toUpperCase(),
                                share.getMtreeId());

                // Get UMFS from cache if possible, otherwise try to retrieve from DB
                UnManagedFileSystem unManagedFS = existingUnManagedFileSystems.get(fsUnManagedFsNativeGuid);
                if (unManagedFS == null) {
                    unManagedFS = getUnManagedFileSystemFromDB(fsUnManagedFsNativeGuid);
                }

                if (unManagedFS != null) {
                    // Add UMFS to cache
                    existingUnManagedFileSystems.put(fsUnManagedFsNativeGuid, unManagedFS);

                    StringSet storagePortIds = unManagedFS.getFileSystemInformation().get(
                            UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
                    StoragePort storagePort = null;
                    for (String portId : storagePortIds) {
                        StoragePort sp = _dbClient.queryObject(StoragePort.class, URI.create(portId));
                        if (sp != null && !sp.getInactive()) {
                            storagePort = sp;
                            break;
                        }
                    }

                    associateCifsExportWithFS(unManagedFS, share, storagePort);

                    unManagedFS.setHasShares(true);
                    unManagedFS.putFileSystemCharacterstics(
                            UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                    .toString(), TRUE);

                    _log.debug("Export map for VNX UMFS {} = {}", unManagedFS.getLabel(), unManagedFS.getUnManagedSmbShareMap());

                    List<UnManagedCifsShareACL> cifsACLs = applyCifsSecurityRules(unManagedFS, share, storagePort);

                    _log.info("Number of export rules discovered for file system {} is {}",
                            unManagedFS.getId() + ":" + unManagedFS.getLabel(), cifsACLs.size());

                    for (UnManagedCifsShareACL cifsAcl : cifsACLs) {
                        _log.info("Unmanaged File share acls : {}", cifsAcl);
                        String fsShareNativeId = cifsAcl.getFileSystemShareACLIndex();
                        _log.info("UMFS Share ACL index {}", fsShareNativeId);
                        String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                .generateNativeGuidForPreExistingFileShare(
                                        storageSystem, fsShareNativeId);
                        _log.info("Native GUID {}", fsUnManagedFileShareNativeGuid);

                        cifsAcl.setNativeGuid(fsUnManagedFileShareNativeGuid);

                        // Check whether the CIFS share ACL was present in ViPR DB.
                        UnManagedCifsShareACL existingACL = checkUnManagedFsCifsACLExistsInDB(_dbClient, cifsAcl.getNativeGuid());
                        if (existingACL == null) {
                            newUnManagedCifsACLs.add(cifsAcl);
                        } else {
                            newUnManagedCifsACLs.add(cifsAcl);
                            existingACL.setInactive(true);
                            oldUnManagedCifsACLs.add(existingACL);
                        }

                    }
                    // Update the UnManaged file system
                    _dbClient.persistObject(unManagedFS);
                }

                if (newUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                    // create new UnManagedCifsShareACL
                    _log.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
                    _dbClient.createObject(newUnManagedCifsACLs);
                    newUnManagedCifsACLs.clear();
                }

                if (oldUnManagedCifsACLs.size() >= VNXFileConstants.VNX_FILE_BATCH_SIZE) {
                    // Update existing UnManagedCifsShareACL
                    _log.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
                    _dbClient.persistObject(oldUnManagedCifsACLs);
                    oldUnManagedCifsACLs.clear();
                }
            }

            if (!newUnManagedCifsACLs.isEmpty()) {
                // create new UnManagedCifsShareACL
                _log.info("Saving Number of New UnManagedCifsShareACL(s) {}", newUnManagedCifsACLs.size());
                _dbClient.createObject(newUnManagedCifsACLs);
                newUnManagedCifsACLs.clear();
            }

            if (!oldUnManagedCifsACLs.isEmpty()) {
                // Update existing UnManagedCifsShareACL
                _log.info("Saving Number of Old UnManagedCifsShareACL(s) {}", oldUnManagedCifsACLs.size());
                _dbClient.persistObject(oldUnManagedCifsACLs);
                oldUnManagedCifsACLs.clear();
            }

            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                    .toString());

            // discovery succeeded
            detailedStatusMessage = String.format("Discovery completed successfully for Data Domain: %s",
                    storageSystem.getId().toString());
            storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);

        } catch (DataDomainApiException dde) {
            _log.error("discoverStorage failed.  Storage system: "
                    + storageSystem.getId());
        } catch (Exception e) {
            _log.error("discoverStorage failed. Storage system: "
                    + storageSystem.getId(), e);
        } finally {
            if (storageSystem != null) {
                try {
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _log.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    private String getMountPount(String shareName, StoragePort storagePort) {

        String mountPoint = null;
        if (storagePort != null) {
            String portName = storagePort.getPortName();
            if (storagePort.getPortNetworkId() != null) {
                portName = storagePort.getPortNetworkId();
            }
            mountPoint = (portName != null) ? "\\\\" + portName + "\\" + shareName : null;
        }

        return mountPoint;

    }

    private List<UnManagedCifsShareACL> applyCifsSecurityRules(UnManagedFileSystem vnxufs,
            DDShareInfoDetail share, StoragePort storagePort) {

        List<UnManagedCifsShareACL> cifsACLs = new ArrayList<UnManagedCifsShareACL>();

        UnManagedCifsShareACL unManagedCifsShareACL = new UnManagedCifsShareACL();
        String shareName = share.getName();

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

    private void associateCifsExportWithFS(UnManagedFileSystem ddufs,
            DDShareInfoDetail share, StoragePort storagePort) {

        try {
            // Assign storage port to unmanaged FS
            if (storagePort != null) {
                StringSet storagePorts = new StringSet();
                storagePorts.add(storagePort.getId().toString());
                ddufs.getFileSystemInformation().remove(UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString());
                ddufs.getFileSystemInformation().put(
                        UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
            }

            String shareName = share.getName();
            String mountPoint = getMountPount(shareName, storagePort);
            UnManagedSMBFileShare unManagedSMBFileShare = new UnManagedSMBFileShare();
            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountPoint);
            // unManagedSMBFileShare.setDescription(share.ge);
            int maxUsers = Integer.MAX_VALUE;

            unManagedSMBFileShare.setMaxUsers(maxUsers);
            unManagedSMBFileShare.setPortGroup(storagePort.getPortGroup());

            unManagedSMBFileShare.setPermission(ShareACL.SupportedPermissions.change.toString());
            // setting to default permission type for DDMC
            unManagedSMBFileShare.setPermissionType(FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW);
            unManagedSMBFileShare.setPath(share.getPath());

            UnManagedSMBShareMap currUnManagedShareMap = ddufs.getUnManagedSmbShareMap();
            if (currUnManagedShareMap == null) {
                currUnManagedShareMap = new UnManagedSMBShareMap();
                ddufs.setUnManagedSmbShareMap(currUnManagedShareMap);
            }

            if (currUnManagedShareMap.get(shareName) == null) {
                currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _log.info("associateCifsExportWithFS - no SMBs already exists for share {}",
                        shareName);
            } else {
                // Remove the existing and add the new share
                currUnManagedShareMap.remove(shareName);
                currUnManagedShareMap.put(shareName, unManagedSMBFileShare);
                _log.warn("associateSMBShareMapWithFS - Identical export already exists for mount path {} Overwrite",
                        shareName);
            }

        } catch (Exception ex) {
            _log.warn("VNX file share retrieve processor failed for path {}, cause {}",
                    share.getPath(), ex);
        }
    }

    /**
     * Build viPR export rules from the export retrieved from the array
     * 
     * @param export - detailed info on the export retrieved from the array
     * @param fileSystemId - URI of the unmanaged
     * @return List of UnManagedFileExportRule
     * @throws IOException
     */
    // TODO:Account for multiple security rules and security flavors
    private List<UnManagedFileExportRule> applyAllSecurityRules(
            DDExportInfoDetail export, URI fileSystemId) {
        List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();

        // hosts --> Map<permission, set of hosts>
        // ruleMap --> Map<security flavor, hosts>
        Map<String, Map<String, StringSet>> ruleMap = new HashMap<>();

        Map<String, DDOptionInfo> ddClients = new HashMap<>();
        for (DDExportClient ddExpClient : export.getClients()) {
            String clientName = ddExpClient.getName();
            String clientOptions = ddExpClient.getOptions();
            DDOptionInfo optionInfo = DDOptionInfo.parseOptions(clientOptions);
            ddClients.put(clientName, optionInfo);

            if (ruleMap.get(optionInfo.security) == null) {
                ruleMap.put(optionInfo.security, new HashMap<String, StringSet>());
            }

            if (optionInfo.permission.equals(DataDomainApiConstants.PERMISSION_RO)) {
                if (ruleMap.get(optionInfo.security).get(DataDomainApiConstants.
                        PERMISSION_RO) == null) {
                    ruleMap.get(optionInfo.security).put(
                            DataDomainApiConstants.PERMISSION_RO, new StringSet());
                }
                ruleMap.get(optionInfo.security).get(DataDomainApiConstants.PERMISSION_RO)
                        .add(clientName);
            }

            if (optionInfo.permission.equals(DataDomainApiConstants.PERMISSION_RW)) {
                if (ruleMap.get(optionInfo.security).get(DataDomainApiConstants.
                        PERMISSION_RW) == null) {
                    ruleMap.get(optionInfo.security).put(
                            DataDomainApiConstants.PERMISSION_RW, new StringSet());
                }
                ruleMap.get(optionInfo.security).get(DataDomainApiConstants.PERMISSION_RW)
                        .add(clientName);
            }
        }

        // Now build the rules.
        Set<String> securityTypes = ruleMap.keySet();
        for (String secType : securityTypes) {
            UnManagedFileExportRule expRule = new UnManagedFileExportRule();
            expRule.setDeviceExportId(export.getId());
            expRule.setFileSystemId(fileSystemId);
            expRule.setExportPath(export.getPath());
            expRule.setMountPoint(export.getPath());
            expRule.setSecFlavor(secType);
            expRule.setAnon(DataDomainApiConstants.ROOT);

            // Read only hosts
            if (ruleMap.get(secType).get(DataDomainApiConstants.PERMISSION_RO) != null) {
                StringSet roHosts = ruleMap.get(secType).get(DataDomainApiConstants.PERMISSION_RO);
                for (String client : roHosts) {
                    if (ddClients.get(client).rootMapping.equals(DataDomainApiConstants.ROOT_SQUASH)) {
                        expRule.setAnon(DataDomainApiConstants.ROOT_SQUASH);
                    }
                }
                expRule.setReadOnlyHosts(roHosts);
            }

            // Read write hosts
            if (ruleMap.get(secType).get(DataDomainApiConstants.PERMISSION_RW) != null) {
                StringSet rwHosts = ruleMap.get(secType).get(DataDomainApiConstants.PERMISSION_RW);
                for (String client : rwHosts) {
                    if (ddClients.get(client).rootMapping.equals(DataDomainApiConstants.ROOT_SQUASH)) {
                        expRule.setAnon(DataDomainApiConstants.ROOT_SQUASH);
                    }
                }
                expRule.setReadWriteHosts(rwHosts);
            }

            expRules.add(expRule);
        }

        return expRules;
    }

}
