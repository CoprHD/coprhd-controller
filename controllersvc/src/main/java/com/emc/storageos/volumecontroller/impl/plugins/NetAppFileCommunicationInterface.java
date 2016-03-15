/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.jettison.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.FileShare;
import com.emc.storageos.db.client.model.Snapshot;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.CopyTypes;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageSystem.SupportedFileReplicationTypes;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedCifsShareACL;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExport;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFSExportMap;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileExportRule;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemCharacterstics;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedFileSystem.SupportedFileSystemInformation;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBFileShare;
import com.emc.storageos.db.client.model.UnManagedDiscoveredObjects.UnManagedSMBShareMap;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.isilon.restapi.IsilonException;
import com.emc.storageos.netapp.NetAppApi;
import com.emc.storageos.netapp.NetAppException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.metering.netapp.NetAppFileCollectionException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.FileControllerConstants;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.plugins.metering.CassandraInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.ZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileDBInsertion;
import com.emc.storageos.volumecontroller.impl.plugins.metering.file.FileZeroRecordGenerator;
import com.emc.storageos.volumecontroller.impl.plugins.metering.netapp.NetAppStatsRecorder;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.utils.UnManagedExportVerificationUtility;
import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.iwave.ext.netapp.AggregateInfo;
import com.iwave.ext.netapp.VFNetInfo;
import com.iwave.ext.netapp.VFilerInfo;
import com.iwave.ext.netapp.model.CifsAcl;
import com.iwave.ext.netapp.model.ExportsHostnameInfo;
import com.iwave.ext.netapp.model.ExportsRuleInfo;
import com.iwave.ext.netapp.model.SecurityRuleInfo;

public class NetAppFileCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {
    private static final int BYTESCONVERTER = 1024;
    private static final String SYSTEM_SERIAL_NUM = "system-serial-number";
    private static final String CPU_FIRMWARE_REL = "cpu-firmware-release";
    private static final String SYSTEM_FIRMWARE_REL = "version";
    private static final String VOL_ROOT = "/vol/";
    private static final String VOL_ROOT_NO_SLASH = "/vol";
    private static final String TRUE = "true";
    private static final String FALSE = "false";
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final String UNMANAGED_FILESYSTEM = "UnManagedFileSystem";
    private static final String UNMANAGED_EXPORT_RULE = "UnManagedExportRule";
    private static final String UNMANAGED_SHARE_ACL = "UnManagedCifsShareACL";
    private static final String RO = "ro";
    private static final String RW = "rw";
    private static final String ROOT = "root";
    private static final String NFS = "NFS";
    private static final String DEFAULT_ANONMOUS_ACCESS = "nobody";
    private static final String ROOT_USER_ACCESS = "root";
    private static final String ROOT_UID = "0";
    private static final String ALL_HOSTS = "All hosts";
    private static final String ROOT_VOL = "/vol0";
    private static final String DEFAULT_FILER = "vfiler0";
    private static final Integer MAX_UMFS_RECORD_SIZE = 1000;
    private static final String MANAGEMENT_INTERFACE = "e0M";
    private static final String SNAPSHOT = ".snapshot";

    private static final String LICENSE_ACTIVATED = "Activated";
    private static final String LICENSE_EVALUATION = "Evaluation";

    private static final Logger _logger = LoggerFactory
            .getLogger(NetAppFileCommunicationInterface.class);

    public static final List<String> ntpPropertiesList = Collections
            .unmodifiableList(Arrays.asList(
                    SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                            .toString(),
                    SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                            .toString(),
                    SupportedNtpFileSystemInformation.STORAGE_POOL.toString(),
                    SupportedNtpFileSystemInformation.NAME.toString(),
                    SupportedNtpFileSystemInformation.VFILER.toString()));

    public enum SupportedNtpFileSystemInformation {
        ALLOCATED_CAPACITY("size-used"), PROVISIONED_CAPACITY("size-total"), STORAGE_POOL(
                "containing-aggregate"), NATIVE_GUID("NativeGuid"), NAME("name"), VFILER("owning-vfiler");

        private String _infoKey;

        SupportedNtpFileSystemInformation(String infoKey) {
            _infoKey = infoKey;
        }

        public String getInfoKey() {
            return _infoKey;
        }

        public static String getFileSystemInformation(String infoKey) {
            for (SupportedNtpFileSystemInformation info : values()) {
                if (infoKey.equals(info.name().toString())) {
                    return info.getInfoKey();
                }
            }
            return null;
        }
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {

        URI storageSystemId = null;
        String fsName = null;

        try {
            _logger.info("Metering for {} using ip {}",
                    accessProfile.getSystemId(), accessProfile.getIpAddress());
            String arrayIp = accessProfile.getIpAddress();
            String arrayUser = accessProfile.getUserName();
            String arrayPassword = accessProfile.getPassword();
            int arrayPort = accessProfile.getPortNumber();
            NetAppApi netAppApi = new NetAppApi.Builder(arrayIp, arrayPort,
                    arrayUser, arrayPassword).https(true).build();
            long latestSampleTime = accessProfile.getLastSampleTime();
            storageSystemId = accessProfile.getSystemId();
            StorageSystem NetAppArray = _dbClient.queryObject(
                    StorageSystem.class, storageSystemId);
            String serialNumber = NetAppArray.getSerialNumber();
            String deviceType = NetAppArray.getSystemType();
            initializeKeyMap(accessProfile);
            List<Stat> stats = new ArrayList<Stat>();

            ZeroRecordGenerator zeroRecordGenerator = new FileZeroRecordGenerator();
            CassandraInsertion statsColumnInjector = new FileDBInsertion();
            /* Get Stats from the NTAP array */
            List<Map<String, String>> usageStats = new ArrayList<Map<String, String>>();
            NetAppStatsRecorder recorder = new NetAppStatsRecorder(zeroRecordGenerator, statsColumnInjector);
            _keyMap.put(Constants._TimeCollected, System.currentTimeMillis());
            Map<String, Number> metrics = new ConcurrentHashMap<String, Number>();

            List<URI> storageSystemIds = new ArrayList<URI>();
            storageSystemIds.add(storageSystemId);
            List<FileShare> fsObjs = _dbClient.queryObjectField(FileShare.class,
                    Constants.STORAGE_DEVICE, storageSystemIds);
            List<URI> fsUris = zeroRecordGenerator.extractVolumesOrFileSharesFromDB(
                    storageSystemId, _dbClient, FileShare.class);
            for (URI fsUri : fsUris) {
                FileShare fsObj = _dbClient.queryObject(FileShare.class, fsUri);
                if (fsObj.getInactive()) {
                    continue;
                }
                fsName = fsObj.getName();
                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        deviceType, serialNumber, fsObj.getPath());
                try {
                    usageStats = netAppApi.listVolumeInfo(fsName, null);
                    for (Map<String, String> map : usageStats) {
                        /*
                         * TODO: usageStats usually contains a single element. If
                         * the list consists of multiple elements, all but one
                         * element will get overwritten.
                         */

                        metrics.put(Constants.SIZE_TOTAL, 0);
                        if (map.get(Constants.SIZE_TOTAL) != null) {
                            metrics.put(Constants.SIZE_TOTAL, Long.valueOf(map.get(Constants.SIZE_TOTAL)));
                        }

                        metrics.put(Constants.SIZE_USED, 0);
                        if (map.get(Constants.SIZE_USED) != null) {
                            metrics.put(Constants.SIZE_USED, Long.valueOf(map.get(Constants.SIZE_USED)));
                        }
                        /*
                         * TODO: Bytes per block on NTAP is hard coded for now. If
                         * possible, we should to get this from the array.
                         */
                        Long snapshotBytesReserved = 0L;
                        if (map.get(Constants.SNAPSHOT_BLOCKS_RESERVED) != null) {
                            snapshotBytesReserved = Long.valueOf(map.get(Constants.SNAPSHOT_BLOCKS_RESERVED))
                                    * Constants.NETAPP_BYTES_PER_BLOCK;
                        }
                        metrics.put(Constants.SNAPSHOT_BYTES_RESERVED, snapshotBytesReserved);
                        Integer snapshotCount = _dbClient.countObjects(Snapshot.class, Constants.PARENT, fsObj.getId());
                        metrics.put(Constants.SNAPSHOT_COUNT, snapshotCount);
                        Stat stat = recorder.addUsageStat(fsNativeGuid, _keyMap, metrics);
                        if (stat != null) {
                            stats.add(stat);
                            // Persists the file system, only if change in used capacity.
                            if (fsObj.getUsedCapacity() != stat.getAllocatedCapacity()) {
                                fsObj.setUsedCapacity(stat.getAllocatedCapacity());
                                _dbClient.persistObject(fsObj);
                            }
                        }
                    }
                } catch (NetAppException ne) {
                    String arg = fsName.toString() + ", " + accessProfile.getIpAddress().toString() +
                            ", " + accessProfile.getSystemType().toString();
                    _logger.info("Failed to retrieve stats for FileShare, Syste, Type: {}", arg);
                }
            }

            if (!stats.isEmpty()) {
                zeroRecordGenerator.identifyRecordstobeZeroed(_keyMap, stats,
                        FileShare.class);
                persistStatsInDB(stats);
                latestSampleTime = System.currentTimeMillis();
                accessProfile.setLastSampleTime(latestSampleTime);
            }
            _logger.info("Done metering device {}", storageSystemId);
        } catch (Exception e) {
            String message = "collectStatisticsInformation failed. Storage system: "
                    + storageSystemId;
            _logger.error(message, e);
            throw NetAppException.exceptions.collectStatsFailed(
                    accessProfile.getIpAddress(), accessProfile.getSystemType(), message);
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
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
        // TODO Auto-generated method stub

    }

    private HashMap<String, List<StorageHADomain>> discoverPortGroups(StorageSystem system,
            List<VFilerInfo> virtualFilers)
            throws NetAppFileCollectionException {

        HashMap<String, List<StorageHADomain>> portGroups = new HashMap<String, List<StorageHADomain>>();
        List<StorageHADomain> newPortGroups = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingPortGroups = new ArrayList<StorageHADomain>();

        _logger.info("Start port group discovery (vfilers) for storage system {}", system.getId());

        NetAppApi netAppApi = new NetAppApi.Builder(system.getIpAddress(),
                system.getPortNumber(), system.getUsername(),
                system.getPassword()).https(true).build();

        StorageHADomain portGroup = null;
        List<VFilerInfo> vFilers = netAppApi.listVFilers(null);
        if (null == vFilers || vFilers.isEmpty()) {

            // Check if default port group was previously created.
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, DEFAULT_FILER, NativeGUIDGenerator.ADAPTER);
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                    results);

            if (results.iterator().hasNext()) {
                StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, results.iterator().next());

                if (tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                    portGroup = tmpGroup;
                    _logger.debug("Found existing port group {} ", tmpGroup.getName());
                }
            }

            if (portGroup == null) {
                portGroup = new StorageHADomain();
                portGroup.setId(URIUtil.createId(StorageHADomain.class));
                portGroup.setName("NetApp");
                portGroup.setVirtual(false);
                portGroup.setNativeGuid(adapterNativeGuid);
                portGroup.setStorageDeviceURI(system.getId());

                StringSet protocols = new StringSet();
                protocols.add(StorageProtocol.File.NFS.name());
                protocols.add(StorageProtocol.File.CIFS.name());
                portGroup.setFileSharingProtocols(protocols);

                newPortGroups.add(portGroup);
            } else {
                existingPortGroups.add(portGroup);
            }
        } else {
            _logger.debug("Number vFilers fouund: {}", vFilers.size());
            virtualFilers.addAll(vFilers);

            StringSet protocols = new StringSet();
            protocols.add(StorageProtocol.File.NFS.name());
            protocols.add(StorageProtocol.File.CIFS.name());

            for (VFilerInfo vf : vFilers) {
                _logger.debug("vFiler name: {}", vf.getName());

                // Check if port group was previously discovered
                URIQueryResultList results = new URIQueryResultList();
                String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vf.getName(), NativeGUIDGenerator.ADAPTER);
                _dbClient.queryByConstraint(
                        AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                        results);

                portGroup = null;
                if (results.iterator().hasNext()) {
                    StorageHADomain tmpGroup = _dbClient.queryObject(StorageHADomain.class, results.iterator().next());

                    if (tmpGroup.getStorageDeviceURI().equals(system.getId())) {
                        portGroup = tmpGroup;
                        _logger.debug("Found duplicate {} ", vf.getName());
                    }
                }

                if (portGroup == null) {
                    portGroup = new StorageHADomain();
                    portGroup.setId(URIUtil.createId(StorageHADomain.class));
                    portGroup.setName(vf.getName());
                    portGroup.setVirtual(true);
                    portGroup.setAdapterType(StorageHADomain.HADomainType.VIRTUAL.toString());
                    portGroup.setNativeGuid(adapterNativeGuid);
                    portGroup.setStorageDeviceURI(system.getId());
                    portGroup.setFileSharingProtocols(protocols);

                    newPortGroups.add(portGroup);
                } else {
                    existingPortGroups.add(portGroup);
                }
            }
        }

        portGroups.put(NEW, newPortGroups);
        portGroups.put(EXISTING, existingPortGroups);
        return portGroups;
    }

    /**
     * Check license is valid or not
     * 
     * @param licenseStatus
     *            Status of the license
     * @param system
     *            Storage System
     * @return true/false
     * @throws IsilonException
     * @throws JSONException
     */
    private boolean isValidLicense(String licenseStatus, StorageSystem system)
            throws IsilonException, JSONException {
        Set<String> validLicenseStatus = new HashSet<String>();
        validLicenseStatus.add(LICENSE_ACTIVATED);
        validLicenseStatus.add(LICENSE_EVALUATION);

        if (validLicenseStatus.contains(licenseStatus)) {
            return true;
        }
        return false;
    }

    private Map<String, List<StoragePool>> discoverStoragePools(StorageSystem system, List<StoragePool> poolsToMatchWithVpool)
            throws NetAppFileCollectionException, NetAppException {

        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        boolean syncLicenseValid = true; // isValidLicense("Licnece", system);

        // Set file replication type for Isilon storage system!!!
        if (syncLicenseValid) {
            StringSet supportReplicationTypes = new StringSet();
            supportReplicationTypes.add(SupportedFileReplicationTypes.REMOTE.name());
            supportReplicationTypes.add(SupportedFileReplicationTypes.LOCAL.name());
            system.setSupportedReplicationTypes(supportReplicationTypes);
        }

        _logger.info("Start storage pool discovery for storage system {}",
                system.getId());
        try {
            NetAppApi netAppApi = new NetAppApi.Builder(system.getIpAddress(),
                    system.getPortNumber(), system.getUsername(),
                    system.getPassword()).https(true).build();

            List<AggregateInfo> pools = netAppApi.listAggregates(null);

            for (AggregateInfo netAppPool : pools) {
                StoragePool pool = null;

                URIQueryResultList results = new URIQueryResultList();
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, netAppPool.getName(), NativeGUIDGenerator.POOL);
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                        results);
                if (results.iterator().hasNext()) {
                    StoragePool tmpPool = _dbClient.queryObject(
                            StoragePool.class, results.iterator().next());

                    if (tmpPool.getStorageDevice().equals(system.getId())) {
                        pool = tmpPool;
                    }
                }
                if (pool == null) {
                    pool = new StoragePool();
                    pool.setId(URIUtil.createId(StoragePool.class));
                    pool.setLabel(poolNativeGuid);
                    pool.setNativeGuid(poolNativeGuid);
                    pool.setPoolServiceType(PoolServiceType.file.toString());
                    pool.setStorageDevice(system.getId());
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY
                            .toString());

                    StringSet protocols = new StringSet();
                    protocols.add("NFS");
                    protocols.add("CIFS");
                    pool.setProtocols(protocols);
                    pool.setPoolName(netAppPool.getName());
                    pool.setNativeId(netAppPool.getName());
                    pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK.toString());
                    Map<String, String> params = new HashMap<String, String>();
                    params.put(StoragePool.ControllerParam.PoolType.name(),
                            "File Pool");
                    pool.addControllerParams(params);
                    pool.setRegistrationStatus(RegistrationStatus.REGISTERED
                            .toString());
                    _logger.info(
                            "Creating new storage pool using NativeGuid : {}",
                            poolNativeGuid);
                    newPools.add(pool);
                } else {
                    existingPools.add(pool);
                }

                // Add the Copy type ASYNC & SYNC, if the Isilon is enabled with SyncIQ service!!
                StringSet copyTypesSupported = new StringSet();

                if (syncLicenseValid) {
                    copyTypesSupported.add(CopyTypes.ASYNC.name());
                    copyTypesSupported.add(CopyTypes.SYNC.name());
                    pool.setSupportedCopyTypes(copyTypesSupported);
                } else {
                    if (pool.getSupportedCopyTypes() != null &&
                            pool.getSupportedCopyTypes().contains(CopyTypes.ASYNC.name())) {
                        pool.getSupportedCopyTypes().remove(CopyTypes.ASYNC.name());
                        pool.getSupportedCopyTypes().remove(CopyTypes.SYNC.name());
                    }
                }
                // Update Pool details with new discovery run
                pool.setTotalCapacity(netAppPool.getSizeTotal()
                        / BYTESCONVERTER);
                pool.setFreeCapacity(netAppPool.getSizeAvailable()
                        / BYTESCONVERTER);
                pool.setSubscribedCapacity(netAppPool.getSizeUsed()
                        / BYTESCONVERTER);

                if (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                        DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name()) ||
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(), DiscoveryStatus.VISIBLE.name())) {
                    poolsToMatchWithVpool.add(pool);
                }
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
        } catch (NumberFormatException e) {
            _logger.error(
                    "Data Format Exception:  Discovery of storage pools failed for storage system {} for {}",
                    system.getId(), e);

            NetAppFileCollectionException ntpe = new NetAppFileCollectionException(
                    "Storage pool discovery data error for storage system "
                            + system.getId());
            ntpe.initCause(e);
            throw ntpe;
        }

        _logger.info("Storage pool discovery for storage system {} complete",
                system.getId());
        storagePools.put(NEW, newPools);
        storagePools.put(EXISTING, existingPools);
        return storagePools;

    }

    /**
     * Discover the Control Station for the specified NTAP File storage array.
     * Since the StorageSystem object currently exists, this method updates
     * information in the object.
     * 
     * @param system
     * @throws NetAppException
     */
    private void discoverFilerInfo(StorageSystem system) throws NetAppFileCollectionException {
        _logger.info("Start Control Station discovery for storage system {}", system.getId());
        Map<String, String> systemInfo = new HashMap<String, String>();
        Map<String, String> systemVer = new HashMap<String, String>();
        NetAppApi nApi = new NetAppApi.Builder(system.getIpAddress(),
                system.getPortNumber(), system.getUsername(),
                system.getPassword()).https(true).build();
        try {
            systemInfo = nApi.systemInfo();
            systemVer = nApi.systemVer();

            if ((null == systemInfo) || (systemInfo.size() <= 0)) {
                _logger.error("Failed to retrieve NetApp Filer info!");
                system.setReachableStatus(false);
                return;
            }

            if ((null == systemVer) || (systemVer.size() <= 0)) {
                _logger.error("Failed to retrieve NetApp Filer info!");
                system.setReachableStatus(false);
                return;
            }
            system.setReachableStatus(true);
            system.setSerialNumber(systemInfo.get(SYSTEM_SERIAL_NUM));
            String sysNativeGuid = NativeGUIDGenerator.generateNativeGuid(system);
            system.setNativeGuid(sysNativeGuid);
            system.setFirmwareVersion(systemVer.get(SYSTEM_FIRMWARE_REL));
            _logger.info(
                    "NetApp Filer discovery for storage system {} complete",
                    system.getId());
        } catch (Exception e) {
            _logger.error("Failed to retrieve NetApp Filer info!");
            system.setReachableStatus(false);
            String msg = "exception occurred while attempting to retrieve NetApp filer information. Storage system: "
                    + system.getIpAddress() + " " + e.getMessage();
            _logger.error(msg);
            throw new NetAppFileCollectionException(msg);
        }
    }

    private Map<String, List<StoragePort>> discoverPorts(StorageSystem storageSystem,
            List<VFilerInfo> vFilers,
            List<StorageHADomain> haDomains)
            throws NetAppFileCollectionException {

        URI storageSystemId = storageSystem.getId();
        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();
        // Discover storage ports
        try {

            _logger.info("discoverPorts for storage system {} - start",
                    storageSystemId);

            StoragePort storagePort = null;
            if (vFilers != null && !vFilers.isEmpty()) {
                for (VFilerInfo filer : vFilers) {
                    for (VFNetInfo intf : filer.getInterfaces()) {
                        if (intf.getNetInterface().equals(MANAGEMENT_INTERFACE)) {
                            continue;
                        }
                        URIQueryResultList results = new URIQueryResultList();
                        String portNativeGuid =
                                NativeGUIDGenerator.generateNativeGuid(storageSystem,
                                        intf.getIpAddress(),
                                        NativeGUIDGenerator.PORT);
                        _dbClient.queryByConstraint(
                                AlternateIdConstraint.Factory
                                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                                results);
                        storagePort = null;
                        if (results.iterator().hasNext()) {
                            StoragePort tmpPort = _dbClient.queryObject(
                                    StoragePort.class, results.iterator()
                                            .next());
                            if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                                    && tmpPort.getPortGroup().equals(filer.getName())) {
                                storagePort = tmpPort;
                                _logger.debug("found duplicate intf {}", intf.getIpAddress());
                            }
                        }

                        if (storagePort == null) {
                            storagePort = new StoragePort();
                            storagePort.setId(URIUtil
                                    .createId(StoragePort.class));
                            storagePort.setTransportType("IP");
                            storagePort.setNativeGuid(portNativeGuid);
                            storagePort.setLabel(portNativeGuid);
                            storagePort.setStorageDevice(storageSystemId);
                            storagePort.setPortName(intf.getIpAddress());
                            storagePort.setPortNetworkId(intf.getIpAddress());
                            storagePort.setPortGroup(filer.getName());
                            storagePort.setStorageHADomain(
                                    findMatchingHADomain(filer.getName(), haDomains));
                            storagePort.setRegistrationStatus(
                                    RegistrationStatus.REGISTERED.toString());
                            _logger.info(
                                    "Creating new storage port using NativeGuid : {}",
                                    portNativeGuid);
                            newStoragePorts.add(storagePort);
                        } else {
                            existingStoragePorts.add(storagePort);
                        }
                        storagePort.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                        storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                    }
                }
            } else {
                // Check if storage port was already discovered
                URIQueryResultList results = new URIQueryResultList();
                String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem, storageSystem.getIpAddress(),
                        NativeGUIDGenerator.PORT);
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                        results);

                if (results.iterator().hasNext()) {
                    StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                            results.iterator().next());
                    if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                            && tmpPort.getPortGroup().equals(
                                    storageSystem.getSerialNumber())) {
                        storagePort = tmpPort;
                        _logger.debug("found duplicate dm intf {}",
                                storageSystem.getSerialNumber());
                    }
                }

                if (storagePort == null) {
                    // Create NetApp storage port for IP address
                    storagePort = new StoragePort();
                    storagePort.setId(URIUtil.createId(StoragePort.class));
                    storagePort.setTransportType("IP");
                    storagePort.setNativeGuid(portNativeGuid);
                    storagePort.setLabel(portNativeGuid);
                    storagePort.setStorageDevice(storageSystemId);
                    storagePort.setPortName(storageSystem.getIpAddress());
                    storagePort.setPortNetworkId(storageSystem.getIpAddress());
                    storagePort.setPortGroup(storageSystem.getSerialNumber());
                    storagePort.setRegistrationStatus(RegistrationStatus.REGISTERED.toString());
                    _logger.info("Creating new storage port using NativeGuid : {}",
                            portNativeGuid);
                    newStoragePorts.add(storagePort);
                } else {
                    existingStoragePorts.add(storagePort);
                }

                storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }

            _logger.info("discoverPorts for storage system {} - complete",
                    storageSystemId);
            storagePorts.put(NEW, newStoragePorts);
            storagePorts.put(EXISTING, existingStoragePorts);
            return storagePorts;
        } catch (Exception e) {
            _logger.error("discoverPorts failed. Storage system: "
                    + storageSystemId, e);
            throw new NetAppFileCollectionException(
                    "discoverPorts failed. Storage system: " + storageSystemId,
                    e);
        }
    }

    @Override
    public void discover(AccessProfile accessProfile)
            throws BaseCollectionException {
        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                                .toString()))) {
            discoverUmanagedFileSystems(accessProfile);
            // discoverUnManagedExports(accessProfile);
            discoverUnManagedNewExports(accessProfile);
            discoverUnManagedCifsShares(accessProfile);
        } else {
            discoverAll(accessProfile);
        }

    }

    private void discoverUmanagedFileSystems(AccessProfile profile) {
        URI storageSystemId = profile.getSystemId();

        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);

        if (null == storageSystem) {
            return;
        }

        String detailedStatusMessage = "Discovery of NetApp Unmanaged FileSystem started";

        List<UnManagedFileSystem> unManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();
        int newFileSystemsCount = 0;
        int existingFileSystemsCount = 0;
        Set<URI> allDiscoveredUnManagedFileSystems = new HashSet<URI>();

        NetAppApi netAppApi = new NetAppApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {

            StoragePort storagePort = getStoragePortPool(storageSystem);

            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem.getId()),
                    storagePoolURIs);

            HashMap<String, StoragePool> pools = new HashMap();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(StoragePool.class, storagePoolURI);
                pools.put(storagePool.getNativeGuid(), storagePool);
            }

            // Retrieve all the file system and vFiler info.
            List<Map<String, String>> fileSystemInfo = netAppApi.listVolumeInfo(null, attrs);
            List<VFilerInfo> vFilers = netAppApi.listVFilers(null);

            for (Map<String, String> fileSystemChar : fileSystemInfo) {
                String poolName = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.STORAGE_POOL
                                        .toString()));

                String filesystem = fileSystemChar
                        .get(SupportedNtpFileSystemInformation
                                .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                                        .toString()));

                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(storageSystem,
                        poolName, NativeGUIDGenerator.POOL);
                StoragePool pool = pools.get(poolNativeGuid);

                String nativeId;
                if (filesystem.startsWith(VOL_ROOT)) {
                    nativeId = filesystem;
                } else {
                    nativeId = VOL_ROOT + filesystem;
                }

                String fsNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        storageSystem.getSystemType(),
                        storageSystem.getSerialNumber(), nativeId);

                // Ignore export for root volume and don't pull it into ViPR db.
                if (nativeId.contains(ROOT_VOL)) {
                    _logger.info("Ignore and not discover root filesystem on NTP array");
                    continue;
                }

                // If the filesystem already exists in db..just continue.No Need
                // to create an UnManaged Filesystems.
                if (checkStorageFileSystemExistsInDB(fsNativeGuid)) {
                    continue;
                }

                _logger.debug("retrieve info for file system: " + filesystem);
                String vFiler = getOwningVfiler(filesystem, fileSystemInfo);

                if (vFiler != null && !vFiler.equalsIgnoreCase(DEFAULT_FILER)) {
                    _logger.info("Ignoring {} because it is owned by {}", filesystem, vFiler);
                    continue;
                }

                String address = getVfilerAddress(vFiler, vFilers);
                if (vFiler != null && !vFiler.isEmpty()) {
                    // Need to use storage port for vFiler.
                    String portNativeGuid =
                            NativeGUIDGenerator.generateNativeGuid(storageSystem,
                                    address,
                                    NativeGUIDGenerator.PORT);

                    storagePort = getVfilerStoragePort(storageSystem, portNativeGuid, vFiler);
                }

                String fsUnManagedFsNativeGuid = NativeGUIDGenerator.generateNativeGuidForPreExistingFileSystem(
                        storageSystem.getSystemType(), storageSystem.getSerialNumber().toUpperCase(), nativeId);

                UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                boolean alreadyExist = unManagedFs == null ? false : true;
                unManagedFs = createUnManagedFileSystem(unManagedFs, profile,
                        fsUnManagedFsNativeGuid, nativeId, storageSystem, pool, filesystem, storagePort, fileSystemChar);
                if (alreadyExist) {
                    existingUnManagedFileSystems.add(unManagedFs);
                    existingFileSystemsCount++;
                } else {
                    unManagedFileSystems.add(unManagedFs);
                    newFileSystemsCount++;
                }
                allDiscoveredUnManagedFileSystems.add(unManagedFs.getId());
                /**
                 * Persist 200 objects and clear them to avoid memory issue
                 */
                validateListSizeLimitAndPersist(unManagedFileSystems, existingUnManagedFileSystems, Constants.DEFAULT_PARTITION_SIZE * 2);
            }

            // Process those active unmanaged fs objects available in database but not in newly discovered items, to mark them inactive.
            markUnManagedFSObjectsInActive(storageSystem, allDiscoveredUnManagedFileSystems);
            _logger.info("New unmanaged Netapp file systems count: {}", newFileSystemsCount);
            _logger.info("Update unmanaged Netapp file systems count: {}", existingFileSystemsCount);
            if (!unManagedFileSystems.isEmpty()) {
                // Add UnManagedFileSystem
                _partitionManager.insertInBatches(unManagedFileSystems,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
            }

            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _partitionManager.updateAndReIndexInBatches(existingUnManagedFileSystems,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
            }
            // discovery succeeds
            detailedStatusMessage = String.format("Discovery completed successfully for NetApp: %s",
                    storageSystemId.toString());

        } catch (NetAppException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
            throw ve;
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
            throw NetAppException.exceptions.discoveryFailed(storageSystemId.toString(), e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
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

    private void discoverUnManagedExports(AccessProfile profile) {

        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);
        Boolean invalidateFS = false;
        if (null == storageSystem) {
            return;
        }

        String detailedStatusMessage = "Discovery of NetApp Unmanaged Exports started";
        List<UnManagedFileSystem> existingUnManagedFileSystems = new ArrayList<UnManagedFileSystem>();

        NetAppApi netAppApi = new NetAppApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {
            URIQueryResultList storagePoolURIs = new URIQueryResultList();
            _dbClient.queryByConstraint(ContainmentConstraint.Factory
                    .getStorageDeviceStoragePoolConstraint(storageSystem
                            .getId()), storagePoolURIs);

            // Get storageport
            HashMap<String, StoragePool> pools = new HashMap<String, StoragePool>();
            Iterator<URI> poolsItr = storagePoolURIs.iterator();
            while (poolsItr.hasNext()) {
                URI storagePoolURI = poolsItr.next();
                StoragePool storagePool = _dbClient.queryObject(
                        StoragePool.class, storagePoolURI);
                pools.put(storagePool.getNativeGuid(), storagePool);
            }

            // Retrieve all the file system and vFiler info.
            List<Map<String, String>> fileSystemInfo = netAppApi.listVolumeInfo(null, attrs);
            List<VFilerInfo> vFilers = netAppApi.listVFilers(null);

            // Get exports on the array and loop through each export.
            List<ExportsRuleInfo> exports = netAppApi.listNFSExportRules(null);
            for (ExportsRuleInfo export : exports) {
                String filesystem = export.getPathname();
                String nativeId = null;
                if (!filesystem.startsWith(VOL_ROOT_NO_SLASH)) {
                    nativeId = VOL_ROOT_NO_SLASH + filesystem;
                } else {
                    nativeId = filesystem;
                }

                // Ignore export for root volume and don't pull it into ViPR db.
                if (filesystem.contains(ROOT_VOL)) {
                    _logger.info("Ignore exports for root volumes on NTP array");
                    continue;
                }

                // Ignore exports that have multiple security rules and security flavors.
                String secflavors = export.getSecurityRuleInfos().get(0).getSecFlavor();
                String[] secFlavorsAry = secflavors.split(",");
                Integer secFlavorAryLength = secFlavorsAry.length;
                Integer secRulesSize = export.getSecurityRuleInfos().size();
                if ((secRulesSize > 1) || (secFlavorAryLength > 1)) {
                    invalidateFS = true;
                } else {
                    invalidateFS = false;
                }

                nativeId = getFSPathIfSubDirectoryExport(nativeId);
                String fsUnManagedFsNativeGuid = NativeGUIDGenerator
                        .generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                nativeId);

                UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                boolean fsAlreadyExists = unManagedFs == null ? false : true;

                if (fsAlreadyExists) {
                    // invalidate FSes that' have multiple security types.
                    // TODO: Come up with list of UMFSes to be presented to API.
                    if (invalidateFS) {
                        _logger.info("FileSystem "
                                + nativeId
                                + "has a complex export with multiple secruity flavors and security rules, hence ignoring the filesystem and NOT brining into ViPR DB");
                        unManagedFs.setInactive(true);
                    } else {
                        _logger.debug("retrieve info for file system: " + filesystem);
                        String vFiler = getOwningVfiler(filesystem, fileSystemInfo);

                        String addr = null;
                        if (vFiler == null || vFiler.isEmpty()) {
                            // No vfilers, use system storage port
                            StoragePort port = getStoragePortPool(storageSystem);
                            addr = port.getPortName();
                        } else {
                            // Use IP address of vFiler.
                            addr = getVfilerAddress(vFiler, vFilers);
                        }

                        UnManagedFSExportMap tempUnManagedExpMap = new UnManagedFSExportMap();
                        createExportMap(export, tempUnManagedExpMap, addr);
                        if (tempUnManagedExpMap.size() > 0) {
                            unManagedFs
                                    .setFsUnManagedExportMap(tempUnManagedExpMap);
                        }
                    }

                    existingUnManagedFileSystems.add(unManagedFs);
                    // Adding this additional logic to avoid OOM
                    if (existingUnManagedFileSystems.size() == MAX_UMFS_RECORD_SIZE) {

                        _partitionManager.updateInBatches(existingUnManagedFileSystems,
                                Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                                UNMANAGED_FILESYSTEM);
                        existingUnManagedFileSystems.clear();
                    }

                } else {
                    _logger.info("FileSystem " + unManagedFs + "is not present in ViPR DB. Hence ignoring " + export + " export");
                }
            }

            if (!existingUnManagedFileSystems.isEmpty()) {
                // Update UnManagedFilesystem
                _partitionManager.updateInBatches(existingUnManagedFileSystems,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_FILESYSTEM);
            }
            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for NetApp: %s",
                    storageSystemId.toString());

        } catch (NetAppException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            throw ve;
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
            throw NetAppException.exceptions.discoveryFailed(storageSystemId.toString(), e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    private void createExportMap(ExportsRuleInfo export,
            UnManagedFSExportMap tempUnManagedExpMap, String storagePort) {

        List<ExportsHostnameInfo> readonlyHosts = export.getSecurityRuleInfos()
                .get(0).getReadOnly();
        List<ExportsHostnameInfo> readwriteHosts = export
                .getSecurityRuleInfos().get(0).getReadWrite();
        List<ExportsHostnameInfo> rootHosts = export.getSecurityRuleInfos()
                .get(0).getRoot();

        UnManagedFSExport tempUnManagedFSROExport = createUnManagedExport(
                export, readonlyHosts, RO, storagePort);
        if (tempUnManagedFSROExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSROExport.getFileExportKey(),
                    tempUnManagedFSROExport);
        }

        UnManagedFSExport tempUnManagedFSRWExport = createUnManagedExport(
                export, readwriteHosts, RW, storagePort);
        if (tempUnManagedFSRWExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSRWExport.getFileExportKey(),
                    tempUnManagedFSRWExport);
        }

        UnManagedFSExport tempUnManagedFSROOTExport = createUnManagedExport(
                export, rootHosts, ROOT, storagePort);
        if (tempUnManagedFSROOTExport != null) {
            tempUnManagedExpMap.put(tempUnManagedFSROOTExport.getFileExportKey(),
                    tempUnManagedFSROOTExport);
        }
    }

    private UnManagedFSExport createUnManagedExport(ExportsRuleInfo export,
            List<ExportsHostnameInfo> typeHosts,
            String permission, String port) {

        List<String> clientList = new ArrayList<String>();
        UnManagedFSExport tempUnManagedFSExport = null;
        if ((null != typeHosts) && !typeHosts.isEmpty()) {

            for (ExportsHostnameInfo client : typeHosts) {
                if ((null != client.getName() && !(clientList.contains(client
                        .getName())))) {
                    if (!clientList.contains(client.getName())) {
                        clientList.add(client.getName());
                    }
                } else if ((null != client.getAllHosts())
                        && (client.getAllHosts())) {
                    // All hosts means empty clientList in ViPR.
                    clientList.clear();
                    _logger.info("Settng ClientList to empty as the export is meant to be accsible to all hosts");
                }
            }

            String anon = export.getSecurityRuleInfos().get(0).getAnon();
            if ((null != anon) && (anon.equals(ROOT_UID))) {
                anon = ROOT_USER_ACCESS;
            }
            else {
                anon = DEFAULT_ANONMOUS_ACCESS;
            }

            tempUnManagedFSExport = new UnManagedFSExport(clientList, port,
                    port + ":" + export.getPathname(), export.getSecurityRuleInfos().get(0)
                            .getSecFlavor(), permission, anon, NFS,
                    port, export.getPathname(), export.getPathname());
        }
        return tempUnManagedFSExport;
    }

    private String getFSPathIfSubDirectoryExport(String nativeId) {
        String[] stgArray = nativeId.split("/");
        if (stgArray.length > 3) {
            return "/" + stgArray[1] + "/" + stgArray[2];
        }
        else {
            return nativeId;
        }
    }

    /**
     * create StorageFileSystem Info Object
     * 
     * @param unManagedFileSystem
     * @param unManagedFileSystemNativeGuid
     * @param storageSystemUri
     * @param storagePool
     * @param fileSystem
     * @param storagePort
     * @param fileSystemChars
     * @return UnManagedFileSystem
     * @throws IOException
     * @throws NetAppException
     */
    private UnManagedFileSystem createUnManagedFileSystem(
            UnManagedFileSystem unManagedFileSystem, AccessProfile profile,
            String unManagedFileSystemNativeGuid, String unManangedFileSystemNativeId,
            StorageSystem system, StoragePool pool,
            String fileSystem, StoragePort storagePort, Map<String, String> fileSystemChars) throws IOException, NetAppException {
        if (null == unManagedFileSystem) {
            unManagedFileSystem = new UnManagedFileSystem();
            unManagedFileSystem.setId(URIUtil
                    .createId(UnManagedFileSystem.class));
            unManagedFileSystem.setNativeGuid(unManagedFileSystemNativeGuid);
            unManagedFileSystem.setStorageSystemUri(system.getId());
            unManagedFileSystem.setHasExports(false);
            unManagedFileSystem.setHasShares(false);
        }

        Map<String, StringSet> unManagedFileSystemInformation = new HashMap<String, StringSet>();
        StringMap unManagedFileSystemCharacteristics = new StringMap();

        // TODO: We are not able to extract this information from filesystem
        // properties from netapp api from iwave.
        // may need to go over all the snapshots/exports to determine if we have
        // any associated filesystems.
        unManagedFileSystemCharacteristics.put(
                SupportedFileSystemCharacterstics.IS_SNAP_SHOT.toString(),
                FALSE);

        unManagedFileSystemCharacteristics.put(
                SupportedFileSystemCharacterstics.IS_THINLY_PROVISIONED
                        .toString(), FALSE);

        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_INGESTABLE
                        .toString(), TRUE);

        // On netapp Systems this currently true.
        unManagedFileSystemCharacteristics.put(
                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                        .toString(), FALSE);

        if (null != storagePort) {
            StringSet storagePorts = new StringSet();
            storagePorts.add(storagePort.getId().toString());
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.STORAGE_PORT.toString(), storagePorts);
        }

        if (null != pool) {
            StringSet pools = new StringSet();
            pools.add(pool.getId().toString());
            unManagedFileSystemInformation.put(
                    UnManagedFileSystem.SupportedFileSystemInformation.STORAGE_POOL.toString(),
                    pools);
            unManagedFileSystem.setStoragePoolUri(pool.getId());
            StringSet matchedVPools = DiscoveryUtils.getMatchedVirtualPoolsForPool(_dbClient, pool.getId());
            _logger.debug("Matched Pools : {}", Joiner.on("\t").join(matchedVPools));
            if (null == matchedVPools || matchedVPools.isEmpty()) {
                // clear all existing supported vpool list.
                unManagedFileSystem.getSupportedVpoolUris().clear();
            } else {
                // replace with new StringSet
                unManagedFileSystem.getSupportedVpoolUris().replace(matchedVPools);
                _logger.info("Replaced Pools :"
                        + Joiner.on("\t").join(unManagedFileSystem.getSupportedVpoolUris()));
            }
        }

        if (null != system) {
            StringSet systemTypes = new StringSet();
            systemTypes.add(system.getSystemType());
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.SYSTEM_TYPE.toString(),
                    systemTypes);
        }

        // Get FileSystem used Space.
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                                .toString()))) {
            StringSet allocatedCapacity = new StringSet();
            allocatedCapacity
                    .add(fileSystemChars.get(SupportedNtpFileSystemInformation
                            .getFileSystemInformation(SupportedNtpFileSystemInformation.ALLOCATED_CAPACITY
                                    .toString())));
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.ALLOCATED_CAPACITY
                            .toString(), allocatedCapacity);
        }

        // Get FileSystem used Space.
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                                .toString()))) {
            StringSet provisionedCapacity = new StringSet();
            provisionedCapacity
                    .add(fileSystemChars.get(SupportedNtpFileSystemInformation
                            .getFileSystemInformation(SupportedNtpFileSystemInformation.PROVISIONED_CAPACITY
                                    .toString())));
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.PROVISIONED_CAPACITY
                            .toString(), provisionedCapacity);
        }

        // Save off FileSystem Name, Path, Mount and label information
        if (null != fileSystemChars
                .get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                                .toString()))) {
            StringSet fsName = new StringSet();
            String fileSystemName = fileSystemChars.get(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                            .toString()));
            fsName.add(fileSystemName);

            unManagedFileSystem.setLabel(fileSystemName);

            StringSet fsPath = new StringSet();
            fsPath.add(unManangedFileSystemNativeId);

            StringSet fsMountPath = new StringSet();
            fsMountPath.add(VOL_ROOT + fileSystem);

            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.NAME.toString(), fsName);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.NATIVE_ID.toString(), fsPath);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.DEVICE_LABEL.toString(), fsName);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.PATH.toString(), fsPath);
            unManagedFileSystemInformation.put(
                    SupportedFileSystemInformation.MOUNT_PATH.toString(), fsMountPath);

        }

        // Add fileSystemInformation and Characteristics.
        unManagedFileSystem
                .addFileSystemInformation(unManagedFileSystemInformation);
        unManagedFileSystem
                .setFileSystemCharacterstics(unManagedFileSystemCharacteristics);
        return unManagedFileSystem;
    }

    /**
     * check Storage fileSystem exists in DB
     * 
     * @param nativeGuid
     * @return
     * @throws IOException
     */
    protected boolean checkStorageFileSystemExistsInDB(String nativeGuid)
            throws IOException {
        URIQueryResultList result = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getFileSystemNativeGUIdConstraint(nativeGuid), result);
        if (result.iterator().hasNext()) {
            return true;
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

    public void discoverAll(AccessProfile accessProfile)
            throws BaseCollectionException {

        URI storageSystemId = null;
        StorageSystem storageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            _logger.info(
                    "Access Profile Details :  IpAddress : {}, PortNumber : {}",
                    accessProfile.getIpAddress(), accessProfile.getPortNumber());
            storageSystemId = accessProfile.getSystemId();
            storageSystem = _dbClient.queryObject(StorageSystem.class,
                    storageSystemId);
            // Retrieve NetApp Filer information.
            discoverFilerInfo(storageSystem);

            String minimumSupportedVersion = VersionChecker.getMinimumSupportedVersion(Type.valueOf(storageSystem.getSystemType()));
            String firmwareVersion = storageSystem.getFirmwareVersion();
            // Example version String for Netapp looks like 8.1.2
            _logger.info("Verifying version details : Minimum Supported Version {} - Discovered NetApp Version {}",
                    minimumSupportedVersion, firmwareVersion);
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, firmwareVersion) < 0)
            {
                storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                storageSystem.setReachableStatus(false);
                DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                NetAppFileCollectionException netAppEx = new NetAppFileCollectionException(String.format(
                        " ** This version of NetApp is not supported ** Should be a minimum of %s", minimumSupportedVersion));
                throw netAppEx;
            }
            storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            storageSystem.setReachableStatus(true);

            _dbClient.persistObject(storageSystem);
            if (!storageSystem.getReachableStatus()) {
                throw new NetAppException("Failed to connect to "
                        + storageSystem.getIpAddress());
            }
            _completer.statusPending(_dbClient, "Identified physical storage");

            List<VFilerInfo> vFilers = new ArrayList<VFilerInfo>();
            Map<String, List<StorageHADomain>> groups = discoverPortGroups(storageSystem, vFilers);
            _logger.info("No of newly discovered groups {}", groups.get(NEW).size());
            _logger.info("No of existing discovered groups {}", groups.get(EXISTING).size());
            if (!groups.get(NEW).isEmpty()) {
                _dbClient.createObject(groups.get(NEW));
            }

            if (!groups.get(EXISTING).isEmpty()) {
                _dbClient.persistObject(groups.get(EXISTING));
            }

            List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
            List<StoragePool> allPools = new ArrayList<StoragePool>();
            Map<String, List<StoragePool>> pools = discoverStoragePools(storageSystem, poolsToMatchWithVpool);
            _logger.info("No of newly discovered pools {}", pools.get(NEW).size());
            _logger.info("No of existing discovered pools {}", pools.get(EXISTING).size());
            if (!pools.get(NEW).isEmpty()) {
                allPools.addAll(pools.get(NEW));
                _dbClient.createObject(pools.get(NEW));
            }

            if (!pools.get(EXISTING).isEmpty()) {
                allPools.addAll(pools.get(EXISTING));
                _dbClient.persistObject(pools.get(EXISTING));
            }
            List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(
                    allPools, _dbClient, storageSystemId);
            if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                poolsToMatchWithVpool.addAll(notVisiblePools);
            }
            _completer.statusPending(_dbClient, "Completed pool discovery");

            // discover ports
            List<StoragePort> allPorts = new ArrayList<StoragePort>();
            Map<String, List<StoragePort>> ports = discoverPorts(storageSystem, vFilers, groups.get(NEW));
            _logger.info("No of newly discovered port {}", ports.get(NEW).size());
            _logger.info("No of existing discovered port {}", ports.get(EXISTING).size());
            if (!ports.get(NEW).isEmpty()) {
                allPorts.addAll(ports.get(NEW));
                _dbClient.createObject(ports.get(NEW));
            }

            if (!ports.get(EXISTING).isEmpty()) {
                allPorts.addAll(ports.get(EXISTING));
                _dbClient.persistObject(ports.get(EXISTING));
            }
            List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(
                    allPorts, _dbClient, storageSystemId);
            _completer.statusPending(_dbClient, "Completed port discovery");
            List<StoragePort> allExistingPorts = new ArrayList<StoragePort>(ports.get(EXISTING));
            if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                allExistingPorts.addAll(notVisiblePorts);
            }
            StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW), allExistingPorts, _dbClient, _coordinator,
                    poolsToMatchWithVpool);

            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for Storage System: %s",
                    storageSystemId.toString());
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format(
                    "Discovery failed for Storage System: %s because %s",
                    storageSystemId.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new NetAppFileCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    /**
     * If discovery fails, then mark the system as unreachable. The discovery
     * framework will remove the storage system from the database.
     * 
     * @param system
     *            the system that failed discovery.
     */
    private void cleanupDiscovery(StorageSystem system) {
        try {
            system.setReachableStatus(false);
            _dbClient.persistObject(system);
        } catch (DatabaseException e) {
            _logger.error(
                    "discoverStorage failed.  Failed to update discovery status to ERROR.",
                    e);
        }

    }

    /**
     * Check if Pool exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
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
        if (!poolURIs.isEmpty()) {
            pool = _dbClient.queryObject(StoragePool.class, poolURIs.get(0));
        }
        return pool;
    }

    private StoragePort getStoragePortPool(StorageSystem storageSystem)
            throws IOException {
        StoragePort storagePort = null;
        // Check if storage port was already discovered
        URIQueryResultList results = new URIQueryResultList();
        String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                storageSystem, storageSystem.getIpAddress(),
                NativeGUIDGenerator.PORT);
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStoragePortByNativeGuidConstraint(portNativeGuid), results);

        if (results.iterator().hasNext()) {
            StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                    results.iterator().next());
            if (tmpPort.getStorageDevice().equals(storageSystem.getId())
                    && tmpPort.getPortGroup().equals(
                            storageSystem.getSerialNumber())) {
                storagePort = tmpPort;
                _logger.debug("found a port for storage system dm intf {}",
                        storageSystem.getSerialNumber());
            }
        }

        return storagePort;
    }

    private URI findMatchingHADomain(String domainName, List<StorageHADomain> haDomains) {
        _logger.debug("domain name to search for: {}", domainName);
        for (StorageHADomain domain : haDomains) {
            _logger.debug("current domain name: {}", domain.getName());
            if (domainName.equals(domain.getName())) {
                _logger.debug("found match for {}", domainName);
                return domain.getId();
            }
        }

        _logger.debug("no match for {}", domainName);
        return null;
    }

    private StoragePort getVfilerStoragePort(StorageSystem storageSystem, String portNativeGuid, String vfiler) {
        URIQueryResultList results = new URIQueryResultList();

        _dbClient.queryByConstraint(
                AlternateIdConstraint.Factory
                        .getStoragePortByNativeGuidConstraint(portNativeGuid),
                results);

        StoragePort port;
        if (results.iterator().hasNext()) {
            port = _dbClient.queryObject(
                    StoragePort.class, results.iterator()
                            .next());
            if (port.getStorageDevice().equals(storageSystem.getId())
                    && port.getPortGroup().equals(vfiler)) {
                _logger.debug("found storage port for vfiler");
                return port;
            }
        }

        return null;
    }

    /**
     * Based on the fileSystem (volume) name, return the name of the vFiler that it belongs to.
     * 
     * File system names are of the form: /vol/<fs name>, /<fs name>, or <fs name>.
     * Names returned from array are only of the form: <fs name>.
     * Therefore, match occurs if file system name 'ends' with the name returned from array.
     * 
     * @param fileSystem name of the file system (volume in NetApp terminology)
     * @param fileSystemInfo list of file system attributes for each file.
     * @return
     */
    private String getOwningVfiler(String fileSystem, List<Map<String, String>> fileSystemInfo) {
        if (fileSystem == null || fileSystem.isEmpty()) {
            _logger.warn("No file system name");
            return null;
        }

        if (fileSystemInfo == null || fileSystemInfo.isEmpty()) {
            _logger.warn("No file system information");
            return null;
        }

        String name = null;
        for (Map<String, String> fileSystemAttrs : fileSystemInfo) {
            name = fileSystemAttrs.get(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(SupportedNtpFileSystemInformation.NAME
                            .toString()));

            if (name != null && (fileSystem.endsWith(name) || (fileSystem.contains("/" + name + "/")))) {
                _logger.debug("found matching file system: " + name);
                return fileSystemAttrs.get(SupportedNtpFileSystemInformation
                        .getFileSystemInformation(SupportedNtpFileSystemInformation.VFILER
                                .toString()));
            }
        }

        return null;
    }

    /**
     * Return the IP address for the specified vFiler.
     * 
     * @param vFilerName name of the vFiler looking for.
     * @param vFilersInfo List of vFilers with their information.
     * @return IP address of the specified vFiler if found, otherwise null.
     */
    private String getVfilerAddress(String vFilerName, List<VFilerInfo> vFilersInfo) {
        if (vFilerName == null || vFilerName.isEmpty()) {
            _logger.warn("No vFiler name specified");
            return null;
        }

        if (vFilersInfo == null || vFilersInfo.isEmpty()) {
            _logger.warn("No vFilerInformation");
            return null;
        }

        for (VFilerInfo info : vFilersInfo) {
            _logger.debug("vFiler info for: " + info.getName());
            if (vFilerName.equals(info.getName())) {
                List<VFNetInfo> netInfo = info.getInterfaces();
                for (VFNetInfo intf : netInfo) {
                    // e0M is the management interface which should be excluded while assigning ports to unmanaged file systems or exports
                    if (intf.getNetInterface().equals(MANAGEMENT_INTERFACE)) {
                        continue;
                    }
                    return intf.getIpAddress();
                }
            }
        }
        return null;
    }

    /**
     * get All Cifs shares in Netapp Device
     * 
     * @param listShares
     * @return
     */
    private HashMap<String, HashSet<UnManagedSMBFileShare>> getAllCifsShares(
            List<Map<String, String>> listShares) {
        // Discover All FileSystem
        HashMap<String, HashSet<UnManagedSMBFileShare>> sharesHapMap =
                new HashMap<String, HashSet<UnManagedSMBFileShare>>();
        UnManagedSMBFileShare unManagedSMBFileShare = null;
        HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet = null;

        // prepare smb shares map elem for each fs path
        for (Map<String, String> shareMap : listShares) {
            String shareName = "";
            String mountpath = "";
            String description = "";
            String maxusers = "-1";
            for (String key : shareMap.keySet()) {
                String value = shareMap.get(key);
                _logger.info("cifs share - key : {} and value : {}", key, value);
                if (null != key) {
                    switch (key) {
                        case "share-name":
                            shareName = value;
                            break;
                        case "mount-point":
                            mountpath = value;
                            break;
                        case "description":
                            description = value;
                            break;
                        case "maxusers":
                            maxusers = value;
                            break;
                        default:
                            break;
                    }
                }
            }
            _logger.info("cifs share details- share-name:{} mount-point: {} ",
                    shareName, mountpath);
            unManagedSMBFileShare = new UnManagedSMBFileShare();
            unManagedSMBFileShare.setName(shareName);
            unManagedSMBFileShare.setMountPoint(mountpath);
            unManagedSMBFileShare.setDescription(description);
            unManagedSMBFileShare.setMaxUsers(Integer.parseInt(maxusers));

            unManagedSMBFileShareHashSet = sharesHapMap.get(mountpath);
            if (null == unManagedSMBFileShareHashSet) {
                unManagedSMBFileShareHashSet = new HashSet<UnManagedSMBFileShare>();
            }
            unManagedSMBFileShareHashSet.add(unManagedSMBFileShare);
            sharesHapMap.put(mountpath, unManagedSMBFileShareHashSet);
        }
        return sharesHapMap;
    }

    /**
     * add Unmanaged SMB share to FS Object
     * 
     * @param unManagedSMBFileShareHashSet
     * @param unManagedSMBShareMap
     * @param addr
     * @param nativeid
     */
    private void createShareMap(
            HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet,
            UnManagedSMBShareMap unManagedSMBShareMap, String addr,
            String nativeid) {

        UnManagedSMBFileShare newUnManagedSMBFileShare = null;
        if (unManagedSMBFileShareHashSet != null && !unManagedSMBFileShareHashSet.isEmpty()) {
            for (UnManagedSMBFileShare unManagedSMBFileShare : unManagedSMBFileShareHashSet) {
                String mountPoint = "\\\\" + addr + "\\" + unManagedSMBFileShare.getName();
                newUnManagedSMBFileShare = new UnManagedSMBFileShare(unManagedSMBFileShare.getName(),
                        unManagedSMBFileShare.getDescription(),
                        // for netApp 7 mode permission and permission type is not used, setting to default values
                        FileControllerConstants.CIFS_SHARE_PERMISSION_TYPE_ALLOW,
                        FileControllerConstants.CIFS_SHARE_PERMISSION_CHANGE,
                        unManagedSMBFileShare.getMaxUsers(),
                        mountPoint);
                newUnManagedSMBFileShare.setNativeId(nativeid);
                newUnManagedSMBFileShare.setPath(nativeid);
                // add new cifs share to File Object
                unManagedSMBShareMap.put(unManagedSMBFileShare.getName(), newUnManagedSMBFileShare);
                _logger.info("New SMB share name: {} has mount point: {}",
                        unManagedSMBFileShare.getName(), mountPoint);
            }
        }
    }

    /**
     * get ACLs for smb shares of fs object
     * 
     * @param unManagedSMBFileShareHashSet
     * @param netAppApi
     * @param fsId
     * @return
     */
    private List<UnManagedCifsShareACL> getACLs(
            HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet,
            NetAppApi netAppApi, URI fsId) {
        // get list of acls for given set of shares
        UnManagedCifsShareACL unManagedCifsShareACL = null;
        List<UnManagedCifsShareACL> unManagedCifsShareACLList =
                new ArrayList<UnManagedCifsShareACL>();
        // get acls for each share
        List<CifsAcl> cifsAclList = null;
        if (unManagedSMBFileShareHashSet != null && !unManagedSMBFileShareHashSet.isEmpty()) {
            for (UnManagedSMBFileShare unManagedSMBFileShare : unManagedSMBFileShareHashSet) {
                // find acl for given share
                String unManagedSMBFileShareName = unManagedSMBFileShare.getName();
                _logger.info("New smb share name: {} and fs : {}",
                        unManagedSMBFileShareName, fsId);

                cifsAclList = netAppApi.listCIFSShareAcl(unManagedSMBFileShareName);
                if (cifsAclList != null && !cifsAclList.isEmpty()) {
                    for (CifsAcl cifsAcl : cifsAclList) {
                        _logger.info("Cifs share ACL: {} ", cifsAcl.toString());
                        unManagedCifsShareACL = new UnManagedCifsShareACL();
                        unManagedCifsShareACL.setShareName(unManagedSMBFileShareName);

                        String user = cifsAcl.getUserName();
                        if (user != null) {
                            unManagedCifsShareACL.setUser(user);
                        } else {
                            unManagedCifsShareACL.setGroup(cifsAcl.getGroupName());
                        }
                        // permission
                        unManagedCifsShareACL.setPermission(cifsAcl.getAccess().name());

                        unManagedCifsShareACL.setId(URIUtil.createId(UnManagedCifsShareACL.class));

                        // filesystem id
                        unManagedCifsShareACL.setFileSystemId(fsId);
                        // add the acl to acl-list
                        unManagedCifsShareACLList.add(unManagedCifsShareACL);
                    }
                }
            }
        }
        return unManagedCifsShareACLList;
    }

    /**
     * discover the unmanaged cifs shares and add shares to vipr db
     * 
     * @param profile
     */
    private void discoverUnManagedCifsShares(AccessProfile profile) {

        URI systemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(StorageSystem.class, systemId);
        if (null == storageSystem) {
            return;
        }
        String detailedStatusMessage = "Discovery of NetApp Unmanaged Cifs shares started";
        NetAppApi netAppApi = new NetAppApi.Builder(storageSystem.getIpAddress(),
                storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword()).https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation.getFileSystemInformation(property));
        }

        try {
            // Used to Save the Acl to DB
            List<UnManagedCifsShareACL> unManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            List<UnManagedCifsShareACL> oldunManagedCifsShareACLList = new ArrayList<UnManagedCifsShareACL>();
            List<Map<String, String>> fileSystemInfo = netAppApi.listVolumeInfo(null, attrs);
            List<VFilerInfo> vFilers = netAppApi.listVFilers(null);
            // Get All cifs shares and ACLs
            List<Map<String, String>> listShares = netAppApi.listShares(null);
            if (listShares != null && !listShares.isEmpty()) {
                _logger.info("total no of shares in netapp system (s) {}", listShares.size());
            }

            HashSet<UnManagedSMBFileShare> unManagedSMBFileShareHashSet = null;
            // prepare the unmanagedSmbshare
            HashMap<String, HashSet<UnManagedSMBFileShare>> unMangedSMBFileShareMapSet = getAllCifsShares(listShares);

            for (String key : unMangedSMBFileShareMapSet.keySet()) {
                String filesystem = key;
                unManagedSMBFileShareHashSet = unMangedSMBFileShareMapSet.get(key);
                _logger.info("FileSystem Path {}", filesystem);

                String nativeId = null;
                if (!filesystem.startsWith(VOL_ROOT_NO_SLASH)) {
                    nativeId = VOL_ROOT_NO_SLASH + filesystem;
                } else {
                    nativeId = filesystem;
                }
                // Ignore root volume and don't pull it into ViPR db.
                if (filesystem.contains(ROOT_VOL)) {
                    _logger.info("Ignore and not discover root filesystem {} on NTP array",
                            filesystem);
                    continue;
                }

                // Ignore snapshots and don't pull it into ViPR db.
                if (filesystem.contains(SNAPSHOT)) {
                    _logger.info("Ignore exports for snapshot {}", filesystem);
                    continue;
                }
                String shareNativeId = getFSPathIfSubDirectoryExport(nativeId);
                String fsUnManagedFsNativeGuid = NativeGUIDGenerator
                        .generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(), shareNativeId);
                UnManagedFileSystem unManagedFs =
                        checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                boolean fsAlreadyExists = unManagedFs == null ? false : true;

                if (fsAlreadyExists) {
                    _logger.info("retrieve info for file system: " + filesystem);
                    String vFiler = getOwningVfiler(filesystem, fileSystemInfo);

                    if (vFiler != null && !vFiler.equalsIgnoreCase(DEFAULT_FILER)) {
                        _logger.info("Ignoring {} because it is owned by {}", filesystem, vFiler);
                        continue;
                    }
                    String addr = null;
                    if (vFiler == null || vFiler.isEmpty()) {
                        // No vfilers, use system storage port
                        StoragePort port = getStoragePortPool(storageSystem);
                        addr = port.getPortName();
                    } else {
                        // Use IP address of vFiler.
                        addr = getVfilerAddress(vFiler, vFilers);
                    }

                    UnManagedSMBShareMap tempUnManagedSMBShareMap = new UnManagedSMBShareMap();
                    // add smb shares to FS object
                    createShareMap(unManagedSMBFileShareHashSet, tempUnManagedSMBShareMap,
                            addr, nativeId);
                    // add shares to fs object
                    if (!tempUnManagedSMBShareMap.isEmpty() && tempUnManagedSMBShareMap.size() > 0) {
                        unManagedFs.setUnManagedSmbShareMap(tempUnManagedSMBShareMap);
                        unManagedFs.setHasShares(true);
                        unManagedFs.putFileSystemCharacterstics(
                                UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                        .toString(), TRUE);
                        _logger.debug("SMB Share map for NetApp UMFS {} = {}",
                                unManagedFs.getLabel(), unManagedFs.getUnManagedSmbShareMap());
                    }

                    List<UnManagedCifsShareACL> tempUnManagedCifsShareAclList =
                            getACLs(unManagedSMBFileShareHashSet, netAppApi, unManagedFs.getId());

                    // get the acl details for given fileshare
                    UnManagedCifsShareACL existingACL = null;
                    for (UnManagedCifsShareACL unManagedCifsShareACL : tempUnManagedCifsShareAclList) {
                        _logger.info("Unmanaged File share acls : {}", unManagedCifsShareACL);
                        String fsShareNativeId = unManagedCifsShareACL.getFileSystemShareACLIndex();
                        _logger.info("UMFS Share ACL index {}", fsShareNativeId);
                        String fsUnManagedFileShareNativeGuid = NativeGUIDGenerator
                                .generateNativeGuidForPreExistingFileShare(storageSystem, fsShareNativeId);
                        _logger.info("Native GUID {}", fsUnManagedFileShareNativeGuid);

                        unManagedCifsShareACL.setNativeGuid(fsUnManagedFileShareNativeGuid);
                        // Check whether the CIFS share ACL was present in ViPR DB.
                        existingACL = checkUnManagedFsCifsACLExistsInDB(_dbClient,
                                unManagedCifsShareACL.getNativeGuid());
                        if (existingACL == null) {
                            unManagedCifsShareACLList.add(unManagedCifsShareACL);
                        } else {
                            // delete the existing acl
                            existingACL.setInactive(true);
                            oldunManagedCifsShareACLList.add(existingACL);
                            // then add new acl
                            unManagedCifsShareACLList.add(unManagedCifsShareACL);
                        }
                    }

                    // save the object
                    {
                        _dbClient.persistObject(unManagedFs);
                        _logger.info("File System {} has Shares and their Count is {}",
                                unManagedFs.getId(), tempUnManagedSMBShareMap.size());
                    }
                    // Adding this additional logic to avoid OOM
                    if (!unManagedCifsShareACLList.isEmpty() &&
                            unManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                        _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}",
                                unManagedCifsShareACLList.size());
                        _dbClient.createObject(unManagedCifsShareACLList);
                        unManagedCifsShareACLList.clear();
                    }
                    if (!oldunManagedCifsShareACLList.isEmpty() &&
                            oldunManagedCifsShareACLList.size() >= MAX_UMFS_RECORD_SIZE) {
                        _logger.info("Update Number of Old UnManagedCifsShareACL(s) {}",
                                oldunManagedCifsShareACLList.size());
                        _dbClient.persistObject(oldunManagedCifsShareACLList);
                        oldunManagedCifsShareACLList.clear();
                    }
                } else {
                    _logger.info("FileSystem " + unManagedFs
                            + "is not present in ViPR DB. Hence ignoring "
                            + filesystem + " share");
                }
            }

            //
            if (!unManagedCifsShareACLList.isEmpty()) {
                _logger.info("Saving Number of New UnManagedCifsShareACL(s) {}",
                        unManagedCifsShareACLList.size());
                _dbClient.createObject(unManagedCifsShareACLList);
            }
            if (!oldunManagedCifsShareACLList.isEmpty()) {
                _logger.info("Saving Number of Old UnManagedCifsShareACL(s) {}",
                        oldunManagedCifsShareACLList.size());
                _dbClient.persistObject(oldunManagedCifsShareACLList);
            }
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for NetApp: %s",
                    systemId.toString());

        } catch (NetAppException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + systemId);
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + systemId, e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }
    }

    private void discoverUnManagedNewExports(AccessProfile profile) {

        URI storageSystemId = profile.getSystemId();
        StorageSystem storageSystem = _dbClient.queryObject(
                StorageSystem.class, storageSystemId);
        if (null == storageSystem) {
            return;
        }

        storageSystem
                .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                        .toString());
        String detailedStatusMessage = "Discovery of NetApp Unmanaged Exports started";

        // Used to Save the rules to DB
        List<UnManagedFileExportRule> newUnManagedExportRules = new ArrayList<UnManagedFileExportRule>();

        NetAppApi netAppApi = new NetAppApi.Builder(
                storageSystem.getIpAddress(), storageSystem.getPortNumber(),
                storageSystem.getUsername(), storageSystem.getPassword())
                .https(true).build();

        Collection<String> attrs = new ArrayList<String>();
        for (String property : ntpPropertiesList) {
            attrs.add(SupportedNtpFileSystemInformation
                    .getFileSystemInformation(property));
        }

        try {
            List<Map<String, String>> fileSystemInfo = netAppApi.listVolumeInfo(null, attrs);
            List<VFilerInfo> vFilers = netAppApi.listVFilers(null);
            // Get exports on the array and loop through each export.
            List<ExportsRuleInfo> exports = netAppApi.listNFSExportRules(null);

            // Verification Utility
            UnManagedExportVerificationUtility validationUtility = new UnManagedExportVerificationUtility(
                    _dbClient);

            for (ExportsRuleInfo deviceExport : exports) {
                String filesystem = deviceExport.getPathname();
                _logger.info("Export Path {}", filesystem);
                String nativeId = null;
                if (!filesystem.startsWith(VOL_ROOT_NO_SLASH)) {
                    nativeId = VOL_ROOT_NO_SLASH + filesystem;
                } else {
                    nativeId = filesystem;
                }

                // Ignore export for root volume and don't pull it into ViPR db.
                if (filesystem.contains(ROOT_VOL)) {
                    _logger.info("Ignore exports for root volume {} on NTP array", filesystem);
                    continue;
                }

                // Ignore export for snapshots and don't pull it into ViPR db.
                if (filesystem.contains(SNAPSHOT)) {
                    _logger.info("Ignore exports for snapshot {}", filesystem);
                    continue;
                }
                nativeId = getFSPathIfSubDirectoryExport(nativeId);
                String fsUnManagedFsNativeGuid = NativeGUIDGenerator
                        .generateNativeGuidForPreExistingFileSystem(
                                storageSystem.getSystemType(), storageSystem
                                        .getSerialNumber().toUpperCase(),
                                nativeId);

                UnManagedFileSystem unManagedFs = checkUnManagedFileSystemExistsInDB(fsUnManagedFsNativeGuid);
                boolean fsAlreadyExists = unManagedFs == null ? false : true;

                // Used as for rules validation
                List<UnManagedFileExportRule> unManagedExportRules = new ArrayList<UnManagedFileExportRule>();

                if (fsAlreadyExists) {
                    _logger.debug("retrieve info for file system: " + filesystem);
                    String vFiler = getOwningVfiler(filesystem, fileSystemInfo);

                    if (vFiler != null && !vFiler.equalsIgnoreCase(DEFAULT_FILER)) {
                        _logger.info("Ignoring {} because it is owned by {}", filesystem, vFiler);
                        continue;
                    }

                    String addr = null;
                    if (vFiler == null || vFiler.isEmpty()) {
                        // No vfilers, use system storage port
                        StoragePort port = getStoragePortPool(storageSystem);
                        addr = port.getPortName();
                    } else {
                        // Use IP address of vFiler.
                        addr = getVfilerAddress(vFiler, vFilers);
                    }

                    UnManagedFSExportMap tempUnManagedExpMap = new UnManagedFSExportMap();
                    // create the export map for FS
                    createExportMap(deviceExport, tempUnManagedExpMap, addr);
                    if (tempUnManagedExpMap.size() > 0) {
                        unManagedFs
                                .setFsUnManagedExportMap(tempUnManagedExpMap);
                        _logger.debug("Export map for NetApp UMFS {} = {}", unManagedFs.getLabel(), unManagedFs.getFsUnManagedExportMap());
                    }

                    List<UnManagedFileExportRule> exportRules = applyAllSecurityRules(deviceExport, addr, unManagedFs.getId());
                    _logger.info("Number of export rules discovered for file system {} is {}", unManagedFs.getId(), exportRules.size());

                    for (UnManagedFileExportRule dbExportRule : exportRules) {
                        _logger.info("Un Managed File Export Rule : {}", dbExportRule);
                        String fsExportRulenativeId = dbExportRule.getFsExportIndex();
                        _logger.info("Native Id using to build Native Guid {}", fsExportRulenativeId);
                        String fsUnManagedFileExportRuleNativeGuid = NativeGUIDGenerator
                                .generateNativeGuidForPreExistingFileExportRule(
                                        storageSystem, fsExportRulenativeId);
                        _logger.info("Native GUID {}", fsUnManagedFileExportRuleNativeGuid);

                        dbExportRule.setNativeGuid(fsUnManagedFileExportRuleNativeGuid);
                        // dbExportRule.setFileSystemId(unManagedFs.getId());
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
                            _logger.info("Validating rules success for export {}", filesystem);
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
                            unManagedFs.setHasExports(true);
                            unManagedFs.putFileSystemCharacterstics(
                                    UnManagedFileSystem.SupportedFileSystemCharacterstics.IS_FILESYSTEM_EXPORTED
                                            .toString(), TRUE);
                            _dbClient.persistObject(unManagedFs);
                            _logger.info("File System {} has Exports and their size is {}", unManagedFs.getId(),
                                    newUnManagedExportRules.size());
                        } else {
                            _logger.warn("Validating rules failed for export {}. Ignroing to import these rules into ViPR DB", filesystem);
                            unManagedFs.setInactive(true);
                            _dbClient.persistObject(unManagedFs);
                        }
                    }
                    // Adding this additional logic to avoid OOM
                    if (newUnManagedExportRules.size() == MAX_UMFS_RECORD_SIZE) {
                        _logger.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                        _partitionManager.updateInBatches(
                                newUnManagedExportRules,
                                Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                                UNMANAGED_EXPORT_RULE);
                        newUnManagedExportRules.clear();
                    }
                }
                else {
                    _logger.info("FileSystem " + unManagedFs
                            + "is not present in ViPR DB. Hence ignoring "
                            + deviceExport + " export");
                }
            }

            if (!newUnManagedExportRules.isEmpty()) {
                _logger.info("Saving Number of UnManagedFileExportRule(s) {}", newUnManagedExportRules.size());
                _partitionManager.updateInBatches(newUnManagedExportRules,
                        Constants.DEFAULT_PARTITION_SIZE, _dbClient,
                        UNMANAGED_EXPORT_RULE);
            }

            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.COMPLETE
                            .toString());
            // discovery succeeds
            detailedStatusMessage = String.format(
                    "Discovery completed successfully for NetApp: %s",
                    storageSystemId.toString());

        } catch (NetAppException ve) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed.  Storage system: "
                    + storageSystemId);
        } catch (Exception e) {
            if (null != storageSystem) {
                cleanupDiscovery(storageSystem);
                storageSystem
                        .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.ERROR
                                .toString());
            }
            _logger.error("discoverStorage failed. Storage system: "
                    + storageSystemId, e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    /**
     * check Pre Existing Storage File Export Rules exists in DB
     * 
     * @param nativeGuid
     * @return unManageFileExport Rule
     * @throws IOException
     */

    // TODO:Account for multiple security rules and security flavors
    private List<UnManagedFileExportRule> applyAllSecurityRules(
            ExportsRuleInfo export, String storagePortAddress, URI fileSystemId) {
        List<UnManagedFileExportRule> expRules = new ArrayList<UnManagedFileExportRule>();
        for (SecurityRuleInfo deviceSecurityRule : export
                .getSecurityRuleInfos()) {

            UnManagedFileExportRule expRule = new UnManagedFileExportRule();
            expRule.setFileSystemId(fileSystemId);
            expRule.setExportPath(export.getPathname());
            expRule.setSecFlavor(deviceSecurityRule.getSecFlavor());
            expRule.setMountPoint(storagePortAddress + ":" + export.getPathname());
            String anon = deviceSecurityRule.getAnon();
            // TODO: This functionality has to be revisited to handle uids for anon.
            if ((null != anon) && (anon.equals(ROOT_UID))) {
                anon = ROOT_USER_ACCESS;
            }
            else {
                anon = DEFAULT_ANONMOUS_ACCESS;
            }
            expRule.setAnon(anon);

            if ((null != deviceSecurityRule.getReadOnly())
                    && !deviceSecurityRule.getReadOnly().isEmpty()) {
                StringSet readOnlyHosts = new StringSet();
                for (ExportsHostnameInfo exportHost : deviceSecurityRule
                        .getReadOnly()) {
                    if (null != exportHost.getName()) {
                        readOnlyHosts.add(exportHost.getName());
                    }
                }
                expRule.setReadOnlyHosts(readOnlyHosts);
            }

            if ((null != deviceSecurityRule.getReadWrite())
                    && !deviceSecurityRule.getReadWrite().isEmpty()) {
                StringSet readWriteHosts = new StringSet();
                for (ExportsHostnameInfo exportHost : deviceSecurityRule
                        .getReadWrite()) {
                    if (null != exportHost.getName()) {
                        readWriteHosts.add(exportHost.getName());
                    }
                }
                expRule.setReadWriteHosts(readWriteHosts);
            }

            if ((null != deviceSecurityRule.getRoot())
                    && !deviceSecurityRule.getRoot().isEmpty()) {
                StringSet rootHosts = new StringSet();
                for (ExportsHostnameInfo exportHost : deviceSecurityRule
                        .getRoot()) {
                    if (null != exportHost.getName()) {
                        rootHosts.add(exportHost.getName());
                    }
                }
                expRule.setRootHosts(rootHosts);
            }
            expRules.add(expRule);
        }

        return expRules;
    }

}
