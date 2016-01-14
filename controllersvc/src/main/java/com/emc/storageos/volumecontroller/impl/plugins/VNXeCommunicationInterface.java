/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.BasicSystemInfo;
import com.emc.storageos.vnxe.models.DiskGroup;
import com.emc.storageos.vnxe.models.PoolTier;
import com.emc.storageos.vnxe.models.RaidGroup;
import com.emc.storageos.vnxe.models.RaidTypeEnum;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.vnxe.models.VNXeCifsServer;
import com.emc.storageos.vnxe.models.VNXeEthernetPort;
import com.emc.storageos.vnxe.models.VNXeFCPort;
import com.emc.storageos.vnxe.models.VNXeFileInterface;
import com.emc.storageos.vnxe.models.VNXeIscsiNode;
import com.emc.storageos.vnxe.models.VNXeIscsiPortal;
import com.emc.storageos.vnxe.models.VNXeNasServer;
import com.emc.storageos.vnxe.models.VNXeNfsServer;
import com.emc.storageos.vnxe.models.VNXePool;
import com.emc.storageos.vnxe.models.VNXeStorageProcessor;
import com.emc.storageos.vnxe.models.VNXeStorageSystem;
import com.emc.storageos.vnxe.models.VNXeStorageTier;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.emc.storageos.volumecontroller.impl.vnxe.VNXeUnManagedObjectDiscoverer;

/**
 * VNXeCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to discover for VNXe using
 * KittyHawk API.
 * 
 */
public class VNXeCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXeCommunicationInterface.class);
    private static final String NEW = "new";
    private static final String EXISTING = "existing";

    // Reference to the Vnxe client factory allows us to get a Vnxe client
    // and execute requests to the Vnxe storage system.
    private VNXeApiClientFactory _clientFactory;
    private VNXeUnManagedObjectDiscoverer unManagedObjectDiscoverer;

    public VNXeCommunicationInterface() {
    };

    public VNXeApiClientFactory getVnxeApiClientFactory() {
        return _clientFactory;
    }

    public void setVnxeApiClientFactory(VNXeApiClientFactory _clientFactory) {
        this._clientFactory = _clientFactory;
    }

    public void setUnManagedObjectDiscoverer(
            VNXeUnManagedObjectDiscoverer volumeDiscoverer) {
        this.unManagedObjectDiscoverer = volumeDiscoverer;
    }

    /**
     * Implementation for scan for Vnxe storage systems.
     * 
     * @param accessProfile
     * 
     * @throws BaseCollectionException
     */
    @Override
    public void scan(AccessProfile accessProfile)
            throws BaseCollectionException {
    }

    /**
     * Implementation for discovery for Vnxe storage systems, file related only
     * for now
     * 
     * @param accessProfile
     * 
     * @throws VNXeException
     */
    @Override
    public void discover(AccessProfile accessProfile) throws VNXeException {

        URI storageSystemURI = accessProfile.getSystemId();
        StorageSystem viprStorageSystem = null;
        String detailedStatusMessage = "Unknown Status";

        try {
            _logger.info(
                    "Access Profile Details :  IpAddress : {}, PortNumber : {}",
                    accessProfile.getIpAddress(), accessProfile.getPortNumber());
            if (null != accessProfile.getnamespace()
                    && (accessProfile
                            .getnamespace()
                            .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES
                                    .toString()) || accessProfile
                            .getnamespace()
                            .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                                    .toString()))) {
                discoverUnmanagedObjects(accessProfile);
            } else {
                // Get the Vnxe storage system from the database.
                viprStorageSystem = _dbClient.queryObject(StorageSystem.class,
                        storageSystemURI);

                _logger.info(String.format(
                        "Discover Vnxe storage system %s at IP:%s, PORT:%s",
                        storageSystemURI.toString(),
                        accessProfile.getIpAddress(),
                        accessProfile.getPortNumber()));

                // Get the vnxe service client for getting information about the
                // Vnxe
                // storage system.
                VNXeApiClient client = getVnxeClient(accessProfile);
                _logger.debug("Got handle to Vnxe service client");

                // Get the serial number and the native guid and set
                // into the storage system.
                _logger.info("Discovering storage system properties.");
                VNXeStorageSystem system = client.getStorageSystem();
                boolean isFASTVPEnabled = false;
                if (system != null) {
                    viprStorageSystem.setSerialNumber(system.getSerialNumber());
                    String guid = NativeGUIDGenerator
                            .generateNativeGuid(viprStorageSystem);
                    viprStorageSystem.setNativeGuid(guid);
                    viprStorageSystem.setLabel(guid);
                    viprStorageSystem
                            .setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
                                    .name());
                    viprStorageSystem.setReachableStatus(true);
                    isFASTVPEnabled = client.isFASTVPEnabled();

                    viprStorageSystem.setAutoTieringEnabled(isFASTVPEnabled);

                    StringSet supportedActions = new StringSet();
                    supportedActions
                            .add(StorageSystem.AsyncActions.CreateElementReplica
                                    .name());
                    supportedActions
                            .add(StorageSystem.AsyncActions.CreateGroupReplica
                                    .name());
                    viprStorageSystem
                            .setSupportedAsynchronousActions(supportedActions);
                    StringSet supportedReplica = new StringSet();
                    supportedReplica
                            .add(StorageSystem.SupportedReplicationTypes.LOCAL
                                    .name());
                    viprStorageSystem
                            .setSupportedReplicationTypes(supportedReplica);

                    _dbClient.persistObject(viprStorageSystem);
                    _completer.statusPending(_dbClient,
                            "Completed discovery of system properties");
                } else {
                    _logger.error("Failed to retrieve VNXe system info!");
                    viprStorageSystem.setReachableStatus(false);
                }
                // get version for the storage system
                BasicSystemInfo info = client.getBasicSystemInfo();
                if (info != null) {
                    viprStorageSystem.setFirmwareVersion(info
                            .getSoftwareVersion());
                }
                StringSet arraySupportedProtocols = new StringSet();
                // Discover the NasServers
                Map<String, URI> nasServerIdMap = new HashMap<String, URI>();
                Map<String, List<StorageHADomain>> nasServers = discoverNasServers(
                        viprStorageSystem, client, nasServerIdMap,
                        arraySupportedProtocols);
                _logger.info("No of newly discovered NasServers {}", nasServers
                        .get(NEW).size());
                _logger.info("No of existing discovered NasServers {}",
                        nasServers.get(EXISTING).size());

                if (!nasServers.get(NEW).isEmpty()) {
                    _dbClient.createObject(nasServers.get(NEW));
                }

                if (!nasServers.get(EXISTING).isEmpty()) {
                    _dbClient.persistObject(nasServers.get(EXISTING));
                }
                _completer.statusPending(_dbClient,
                        "Completed NAS Server discovery");

                // Discover FileInterfaces
                List<StoragePort> allExistingPorts = new ArrayList<StoragePort>();
                List<StoragePort> allNewPorts = new ArrayList<StoragePort>();
                Map<String, List<StoragePort>> ports = discoverFileStoragePorts(
                        viprStorageSystem, client, nasServerIdMap);

                if (ports.get(NEW) != null && !ports.get(NEW).isEmpty()) {
                    allNewPorts.addAll(ports.get(NEW));
                    _dbClient.createObject(ports.get(NEW));
                }

                if (ports.get(EXISTING) != null && !ports.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(ports.get(EXISTING));
                    _dbClient.persistObject(ports.get(EXISTING));
                }
                _completer.statusPending(_dbClient,
                        "Completed file ports discovery");

                // discover storage processors
                Map<String, URI> spIdMap = new HashMap<String, URI>();
                Map<String, List<StorageHADomain>> sps = discoverStorageProcessors(
                        viprStorageSystem, client, spIdMap);

                if (!sps.get(NEW).isEmpty()) {
                    _dbClient.createObject(sps.get(NEW));
                }

                if (!sps.get(EXISTING).isEmpty()) {
                    _dbClient.persistObject(sps.get(EXISTING));
                }
                _completer.statusPending(_dbClient,
                        "Completed storage processor discovery");

                // discover iscsi ports
                Map<String, List<StoragePort>> iscsiPorts = discoverIscsiPorts(
                        viprStorageSystem, client, spIdMap);
                boolean hasIscsiPorts = false;
                if (iscsiPorts.get(NEW) != null && !iscsiPorts.get(NEW).isEmpty()) {
                    allNewPorts.addAll(iscsiPorts.get(NEW));
                    hasIscsiPorts = true;
                    _dbClient.createObject(iscsiPorts.get(NEW));
                }

                if (iscsiPorts.get(EXISTING) != null && !iscsiPorts.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(iscsiPorts.get(EXISTING));
                    hasIscsiPorts = true;
                    _dbClient.persistObject(ports.get(EXISTING));
                }
                if (hasIscsiPorts) {
                    arraySupportedProtocols.add(StorageProtocol.Block.iSCSI
                            .name());
                }
                _completer.statusPending(_dbClient,
                        "Completed iscsi ports discovery");

                // discover fc ports
                Map<String, List<StoragePort>> fcPorts = discoverFcPorts(
                        viprStorageSystem, client, spIdMap);
                boolean hasFcPorts = false;
                if (fcPorts.get(NEW) != null && !fcPorts.get(NEW).isEmpty()) {
                    allNewPorts.addAll(fcPorts.get(NEW));
                    hasFcPorts = true;
                    _dbClient.createObject(fcPorts.get(NEW));
                }

                if (fcPorts.get(EXISTING) != null && !fcPorts.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(fcPorts.get(EXISTING));
                    hasFcPorts = true;
                    _dbClient.persistObject(ports.get(EXISTING));
                }
                if (hasFcPorts) {
                    arraySupportedProtocols
                            .add(StorageProtocol.Block.FC.name());
                }
                _completer.statusPending(_dbClient,
                        "Completed FC ports discovery");

                List<StoragePort> allPorts = new ArrayList<StoragePort>(allNewPorts);
                allPorts.addAll(allExistingPorts);
                // check if any port not visible in this discovery
                List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(
                        allPorts, _dbClient, viprStorageSystem.getId());
                if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                    allExistingPorts.addAll(notVisiblePorts);
                }
                /**
                 * Discover the VNXe pool information.
                 */
                _logger.info("Discovering storage pools.");
                List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
                List<StoragePool> allPools = new ArrayList<StoragePool>();
                Map<String, List<StoragePool>> pools = discoverStoragePools(
                        viprStorageSystem, client, arraySupportedProtocols,
                        poolsToMatchWithVpool);

                _logger.info("No of newly discovered pools {}", pools.get(NEW)
                        .size());
                _logger.info("No of existing discovered pools {}",
                        pools.get(EXISTING).size());
                if (!pools.get(NEW).isEmpty()) {
                    allPools.addAll(pools.get(NEW));
                    _dbClient.createObject(pools.get(NEW));
                    StoragePoolAssociationHelper.setStoragePoolVarrays(
                            viprStorageSystem.getId(), pools.get(NEW),
                            _dbClient);
                }

                if (!pools.get(EXISTING).isEmpty()) {
                    allPools.addAll(pools.get(EXISTING));
                    _dbClient.persistObject(pools.get(EXISTING));
                }

                List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(
                        allPools, _dbClient, viprStorageSystem.getId());
                if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                    poolsToMatchWithVpool.addAll(notVisiblePools);
                }

                StoragePortAssociationHelper.runUpdatePortAssociationsProcess(allNewPorts, allExistingPorts, _dbClient, _coordinator,
                        poolsToMatchWithVpool);
                _completer.statusPending(_dbClient, "Completed pool discovery");

                /**
                 * Discover AutoTieringPolicies and StorageTiers if FASTVP
                 * enabled.
                 */

                if (isFASTVPEnabled) {
                    _logger.info("FASTVP is enabled");
                    HashMap<String, List<AutoTieringPolicy>> policies = discoverAutoTierPolicies(
                            viprStorageSystem, client);
                    if (!policies.get(NEW).isEmpty()) {
                        _dbClient.createObject(policies.get(NEW));
                    }

                    if (!policies.get(EXISTING).isEmpty()) {
                        _dbClient.persistObject(policies.get(EXISTING));
                    }

                    HashMap<String, List<StorageTier>> tiers = discoverStorageTier(
                            viprStorageSystem, client);
                    if (!tiers.get(NEW).isEmpty()) {
                        _dbClient.createObject(tiers.get(NEW));
                    }

                    if (!tiers.get(EXISTING).isEmpty()) {
                        _dbClient.persistObject(tiers.get(EXISTING));
                    }
                }
                detailedStatusMessage = String
                        .format("Discovery completed successfully for Storage System: %s",
                                storageSystemURI.toString());

            }
        } catch (Exception e) {
            detailedStatusMessage = String.format(
                    "Discovery failed for VNXe %s: %s",
                    storageSystemURI.toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw VNXeException.exceptions.discoveryError("Discovery error", e);
        } finally {
            if (viprStorageSystem != null) {
                try {
                    // set detailed message
                    viprStorageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(viprStorageSystem);
                } catch (DatabaseException ex) {
                    _logger.error("Error while persisting object to DB", ex);
                }
            }
        }

    }

    private void discoverUnmanagedObjects(AccessProfile accessProfile) {
        StorageSystem storageSystem = null;
        String detailedStatusMessage = null;
        try {
            storageSystem = _dbClient.queryObject(StorageSystem.class,
                    accessProfile.getSystemId());
            if (null == storageSystem) {
                return;
            }

            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                            .toString());
            _dbClient.persistObject(storageSystem);
            if (accessProfile.getnamespace().equals(
                    StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS
                            .toString())) {
                unManagedObjectDiscoverer.discoverUnManagedFileSystems(
                        accessProfile, _dbClient, _coordinator,
                        _partitionManager);
                unManagedObjectDiscoverer.discoverAllExportRules(accessProfile,
                        _dbClient, _partitionManager);
                unManagedObjectDiscoverer.discoverAllCifsShares(accessProfile,
                        _dbClient, _partitionManager);

            } else if (accessProfile.getnamespace().equals(
                    StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES
                            .toString())) {
                unManagedObjectDiscoverer.discoverUnManagedVolumes(
                        accessProfile, _dbClient, _coordinator,
                        _partitionManager);
            }

            // discovery succeeds
            detailedStatusMessage = String
                    .format("UnManaged Object Discovery completed successfully for VNXe: %s",
                            storageSystem.getId().toString());
            _logger.info(detailedStatusMessage);

        } catch (Exception e) {
            detailedStatusMessage = String
                    .format("Discovery of unmanaged volumes failed for system %s because %s",
                            storageSystem.getId().toString(),
                            e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw VNXeException.exceptions.discoveryError(
                    "Unmanaged objectobject discovery error", e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error(
                            "Error while updating unmanaged object discovery status for system.",
                            ex);
                }
            }
        }
    }

    /**
     * Get the Vnxe service client for making requests to the Vnxe based on the
     * passed profile.
     * 
     * @param accessProfile
     *            A reference to the access profile.
     * 
     * @return A reference to the Vnxe service client.
     */
    private VNXeApiClient getVnxeClient(AccessProfile accessProfile) {
        VNXeApiClient client = _clientFactory.getClient(
                accessProfile.getIpAddress(), accessProfile.getPortNumber(),
                accessProfile.getUserName(), accessProfile.getPassword());

        return client;

    }

    /**
     * Returns the list of storage pools for the specified VNXe storage system.
     * 
     * @param system
     *            storage system information.
     * @param client
     *            VNXe service client
     * @param supportedProtocols
     *            calculated supportedProtocols for the array
     * @return Map of New and Existing known storage pools.
     * @throws VNXeException
     */
    private Map<String, List<StoragePool>> discoverStoragePools(
            StorageSystem system, VNXeApiClient client,
            StringSet supportedProtocols,
            List<StoragePool> poolsToMatchWithVpool) throws VNXeException {

        Map<String, List<StoragePool>> storagePools = new HashMap<String, List<StoragePool>>();

        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> existingPools = new ArrayList<StoragePool>();

        _logger.info("Start storage pool discovery for storage system {}",
                system.getId());
        try {
            List<VNXePool> pools = client.getPools();

            for (VNXePool vnxePool : pools) {
                StoragePool pool = null;

                URIQueryResultList results = new URIQueryResultList();
                String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                        system, vnxePool.getId(), NativeGUIDGenerator.POOL);
                _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStoragePoolByNativeGuidConstraint(poolNativeGuid),
                        results);
                boolean isModified = false;
                if (results.iterator().hasNext()) {
                    StoragePool tmpPool = _dbClient.queryObject(
                            StoragePool.class, results.iterator().next());

                    if (tmpPool.getStorageDevice().equals(system.getId())) {
                        pool = tmpPool;
                        _logger.info("Found StoragePool {} at {}",
                                pool.getPoolName(), poolNativeGuid);
                    }
                }

                if (pool == null) {
                    pool = new StoragePool();
                    pool.setId(URIUtil.createId(StoragePool.class));

                    pool.setLabel(poolNativeGuid);
                    pool.setNativeGuid(poolNativeGuid);
                    pool.setOperationalStatus(vnxePool.getStatus());
                    pool.setPoolServiceType(PoolServiceType.block_file
                            .toString());
                    pool.setStorageDevice(system.getId());
                    pool.setProtocols(supportedProtocols);
                    pool.setNativeId(vnxePool.getId());
                    pool.setPoolName(vnxePool.getName());
                    pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE
                            .name());
                    StringSet raidLevels = new StringSet();
                    RaidTypeEnum raid = vnxePool.getRaidTypeEnum();
                    if (raid != null) {
                        raidLevels.add(vnxePool.getRaidTypeEnum().name());
                        pool.setSupportedRaidLevels(raidLevels);
                    }
                    // Supported resource type indicates what type of file
                    // systems are supported.
                    pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_AND_THICK
                            .toString());
                    pool.setPoolClassName(StoragePool.PoolClassNames.VNXe_Pool
                            .name());
                    pool.setPoolServiceType(StoragePool.PoolServiceType.block_file
                            .name());
                    pool.setAutoTieringEnabled(getPoolAutoTieringEnabled(
                            vnxePool, system));

                    pool.setRegistrationStatus(RegistrationStatus.REGISTERED
                            .toString());
                    _logger.info(
                            "Creating new storage pool using NativeGuid : {}",
                            poolNativeGuid);
                    newPools.add(pool);
                } else {
                    // update pool attributes
                    _logger.info("updating the pool: {}", poolNativeGuid);
                    pool.setOperationalStatus(vnxePool.getStatus());
                    if (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getProtocols(), supportedProtocols)) {
                        isModified = true;
                    }
                    pool.setProtocols(supportedProtocols);
                    StringSet raidLevels = new StringSet();
                    RaidTypeEnum raid = vnxePool.getRaidTypeEnum();
                    if (raid != null) {
                        raidLevels.add(vnxePool.getRaidTypeEnum().name());
                        pool.setSupportedRaidLevels(raidLevels);
                    }
                    pool.setAutoTieringEnabled(getPoolAutoTieringEnabled(
                            vnxePool, system));
                    existingPools.add(pool);
                }

                List<PoolTier> poolTiers = vnxePool.getTiers();
                StringSet diskTypes = new StringSet();
                for (PoolTier poolTier : poolTiers) {

                    List<RaidGroup> raidGroups = poolTier.getRaidGroups();
                    for (RaidGroup raidGroup : raidGroups) {
                        VNXeBase diskGroup = raidGroup.getDiskGroup();
                        if (diskGroup != null) {
                            DiskGroup diskgroupObj = client
                                    .getDiskGroup(diskGroup.getId());
                            diskTypes.add(diskgroupObj.getDiskTechnologyEnum()
                                    .name());
                        }
                    }
                }
                pool.setSupportedDriveTypes(diskTypes);

                double size = vnxePool.getSizeTotal();
                if (size > 0) {
                    pool.setTotalCapacity(VNXeUtils.convertDoubleSizeToViPRLong(size)); // convert to kb
                }

                long free = VNXeUtils.convertDoubleSizeToViPRLong(vnxePool.getSizeFree());
                if (free > 0) {
                    pool.setFreeCapacity(free);
                    pool.setMaximumThinVolumeSize(free);
                    pool.setMaximumThickVolumeSize(free);
                }
                long subscribed = VNXeUtils.convertDoubleSizeToViPRLong(vnxePool.getSizeSubscribed());
                pool.setSubscribedCapacity(subscribed);
                if (isModified
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                                DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name())
                        || ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(),
                                DiscoveryStatus.VISIBLE.name())) {
                    poolsToMatchWithVpool.add(pool);
                }
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                pool.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            }
        } catch (VNXeException e) {
            _logger.error(
                    "Discovery of storage pools failed for storage system {} for {}",
                    system.getId(), e.getMessage());
            throw e;
        }
        for (StoragePool newPool : newPools) {
            _logger.info("New Storage Pool : " + newPool);
            _logger.info("New Storage Pool : {} : {}", newPool.getNativeGuid(),
                    newPool.getId());
        }
        for (StoragePool pool : existingPools) {
            _logger.info("Old Storage Pool : " + pool);
            _logger.info("Old Storage Pool : {} : {}", pool.getNativeGuid(),
                    pool.getId());
        }
        // return storagePools;
        storagePools.put(NEW, newPools);
        storagePools.put(EXISTING, existingPools);
        _logger.info("Number of pools found {} : ", storagePools.size());
        _logger.info("Storage pool discovery for storage system {} complete",
                system.getId());
        return storagePools;
    }

    /**
     * Discover the NasServer (Port Groups) for the specified VNXe storage
     * array.
     * 
     * @param system
     *            storage system information
     * @param client
     *            VNXeServiceClient
     * @param nasServerIdMap
     *            all valid nasServer id map
     * @param arraySupportedProtocol
     *            array supported protocol
     * @return Map of New and Existing NasServers
     * @throws VNXeException
     */
    private HashMap<String, List<StorageHADomain>> discoverNasServers(
            StorageSystem system, VNXeApiClient client,
            Map<String, URI> nasServerIdMap, StringSet arraySupportedProtocols)
            throws VNXeException {
        HashMap<String, List<StorageHADomain>> allNasServers = new HashMap<String, List<StorageHADomain>>();

        List<StorageHADomain> newNasServers = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingNasServers = new ArrayList<StorageHADomain>();
        boolean isNFSSupported = false;
        boolean isCIFSSupported = false;
        boolean isBothSupported = false;
        _logger.info("Start NasServer discovery for storage system {}",
                system.getId());

        List<VNXeNasServer> nasServers = client.getNasServers();
        List<VNXeCifsServer> cifsServers = client.getCifsServers();
        List<VNXeNfsServer> nfsServers = client.getNfsServers();

        for (VNXeNasServer nasServer : nasServers) {
            StorageHADomain haDomain = null;
            if (null == nasServer) {
                _logger.debug("Null data mover in list of port groups.");
                continue;
            }
            if (nasServer.getMode() == VNXeNasServer.NasServerModeEnum.DESTINATION) {
                _logger.debug("Found a replication destination NasServer");
                continue;
            }

            if (nasServer.getIsSystem()) {
                // skip system nasServer
                continue;
            }
            StringSet protocols = new StringSet();
            // Check if port group was previously discovered
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, nasServer.getName(), NativeGUIDGenerator.ADAPTER);
            _dbClient
                    .queryByConstraint(
                            AlternateIdConstraint.Factory
                                    .getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                            results);

            if (results.iterator().hasNext()) {
                StorageHADomain tmpDomain = _dbClient.queryObject(
                        StorageHADomain.class, results.iterator().next());

                if (tmpDomain.getStorageDeviceURI().equals(system.getId())) {
                    haDomain = tmpDomain;
                    _logger.debug("Found duplicate {} ", nasServer.getName());
                }
            }
            // get the supported protocol on the nasServer
            if (cifsServers != null && !cifsServers.isEmpty()) {
                for (VNXeCifsServer cifsServer : cifsServers) {
                    if (cifsServer.getNasServer().getId()
                            .equals(nasServer.getId())) {
                        protocols.add(StorageProtocol.File.CIFS.name());
                        isCIFSSupported = true;
                        break;
                    }
                }
            }

            if (nfsServers != null && !nfsServers.isEmpty()) {
                for (VNXeNfsServer nfsServer : nfsServers) {
                    if (nfsServer.getNasServer().getId()
                            .equals(nasServer.getId())) {
                        protocols.add(StorageProtocol.File.NFS.name());
                        isNFSSupported = true;
                        break;
                    }
                }
            }

            if (protocols.size() == 2) {
                // this nasServer support both
                isBothSupported = true;
            }
            // If the nasServer was not previously discovered
            if (haDomain == null) {
                haDomain = new StorageHADomain();
                haDomain.setId(URIUtil.createId(StorageHADomain.class));
                haDomain.setNativeGuid(adapterNativeGuid);
                haDomain.setStorageDeviceURI(system.getId());
                haDomain.setAdapterName(nasServer.getName());
                haDomain.setName(nasServer.getName());
                haDomain.setSerialNumber(nasServer.getId());
                haDomain.setFileSharingProtocols(protocols);
                newNasServers.add(haDomain);
            } else {
                haDomain.setFileSharingProtocols(protocols);
                existingNasServers.add(haDomain);
            }
            nasServerIdMap.put(nasServer.getId(), haDomain.getId());
        }
        if (isBothSupported) {
            arraySupportedProtocols.add(StorageProtocol.File.NFS.name());
            arraySupportedProtocols.add(StorageProtocol.File.CIFS.name());
        } else if (isNFSSupported && isCIFSSupported) {
            arraySupportedProtocols
                    .add(StorageProtocol.File.NFS_OR_CIFS.name());
        } else if (isNFSSupported) {
            arraySupportedProtocols.add(StorageProtocol.File.NFS.name());
        } else if (isCIFSSupported) {
            arraySupportedProtocols.add(StorageProtocol.File.CIFS.name());
        }

        _logger.info("NasServer discovery for storage system {} complete.",
                system.getId());
        for (StorageHADomain newDomain : newNasServers) {
            _logger.info("New NasServer : {} : {}", newDomain.getNativeGuid(),
                    newDomain.getId());
        }
        for (StorageHADomain domain : existingNasServers) {
            _logger.info("Existing NasServer : {} : {}",
                    domain.getNativeGuid(), domain.getId());
        }
        // return portGroups;
        allNasServers.put(NEW, newNasServers);
        allNasServers.put(EXISTING, existingNasServers);
        return allNasServers;
    }

    /**
     * Discover file interfaces for specified VNXe Storage Array
     * 
     * @param system
     *            storage system.
     * @param client
     *            VNXe service client
     * @param nasServerIdSet
     *            all valid NAS Server ids
     * @return Map of New and Existing Storage Ports
     * @throws VNXeException
     */
    private HashMap<String, List<StoragePort>> discoverFileStoragePorts(
            StorageSystem system, VNXeApiClient client,
            Map<String, URI> nasServerIdMap) throws VNXeException {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info("Start storage port discovery for storage system {}",
                system.getId());

        // Retrieve the list of data movers interfaces for the VNX File device.
        List<VNXeFileInterface> interfaces = client.getFileInterfaces();
        if (interfaces == null || interfaces.isEmpty()) {
            _logger.info("No file interfaces found for the system: {} ",
                    system.getId());
            return storagePorts;
        }
        _logger.info("Number file interfaces found: {}", interfaces.size());
        // Create the list of storage ports.
        for (VNXeFileInterface intf : interfaces) {
            StoragePort port = null;

            // Check for valid nasServer
            VNXeBase nasServer = intf.getNasServer();
            if (nasServer == null) {
                continue;
            }
            String nasServerId = nasServer.getId();
            URI haDomainUri = nasServerIdMap.get(nasServerId);
            if (haDomainUri == null) {
                continue;
            }

            // Check if storage port was already discovered
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, intf.getIpAddress(), NativeGUIDGenerator.PORT);

            URIQueryResultList results = new URIQueryResultList();

            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortByNativeGuidConstraint(portNativeGuid),
                    results);

            if (results.iterator().hasNext()) {
                _logger.info("cross verifying for duplicate port");

                StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                        results.iterator().next());

                _logger.info(String
                        .format("StorageDevice found for port %s - Actual StorageDevice %s : PortGroup found for port %s - Actual PortGroup %s",
                                tmpPort.getStorageDevice(), system.getId(),
                                tmpPort.getPortGroup(), nasServerId));

                if (tmpPort.getStorageDevice().equals(system.getId())
                        && tmpPort.getPortGroup().equals(nasServerId)) {
                    port = tmpPort;
                    _logger.info("found duplicate dm intf {}", intf.getName());
                }
            }

            // If data mover interface was not previously discovered, add new
            // storage port
            if (port == null) {
                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setLabel(portNativeGuid);
                port.setTransportType("IP");
                port.setNativeGuid(portNativeGuid);
                port.setStorageDevice(system.getId());
                port.setRegistrationStatus(RegistrationStatus.REGISTERED
                        .toString());
                port.setPortName(intf.getName());
                port.setPortNetworkId(intf.getIpAddress());
                port.setPortGroup(nasServerId);
                port.setStorageHADomain(haDomainUri);
                _logger.info(
                        "Creating new storage port using NativeGuid : {}, IP : {}",
                        portNativeGuid, intf.getIpAddress());
                newStoragePorts.add(port);
            } else {
                existingStoragePorts.add(port);
            }
            port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        }

        _logger.info("Storage port discovery for storage system {} complete",
                system.getId());

        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        return storagePorts;
    }

    private HashMap<String, List<StorageHADomain>> discoverStorageProcessors(
            StorageSystem system, VNXeApiClient client, Map<String, URI> spIdMap)
            throws VNXeException {
        HashMap<String, List<StorageHADomain>> result = new HashMap<String, List<StorageHADomain>>();

        List<StorageHADomain> newSPs = new ArrayList<StorageHADomain>();
        List<StorageHADomain> existingSPs = new ArrayList<StorageHADomain>();

        _logger.info("Start storage processor discovery for storage system {}",
                system.getId());
        List<VNXeStorageProcessor> sps = client.getStorageProcessors();

        for (VNXeStorageProcessor sp : sps) {
            StorageHADomain haDomain = null;
            if (null == sp) {
                _logger.debug("Null sp in the list of storage processors.");
                continue;
            }

            // Check if sp was previously discovered
            URIQueryResultList results = new URIQueryResultList();
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, sp.getId(), NativeGUIDGenerator.ADAPTER);
            _dbClient
                    .queryByConstraint(
                            AlternateIdConstraint.Factory
                                    .getStorageHADomainByNativeGuidConstraint(adapterNativeGuid),
                            results);

            if (results.iterator().hasNext()) {
                StorageHADomain tmpDomain = _dbClient.queryObject(
                        StorageHADomain.class, results.iterator().next());

                if (tmpDomain.getStorageDeviceURI().equals(system.getId())) {
                    haDomain = tmpDomain;
                    _logger.debug("Found existing {} ", sp.getId());
                }
            }

            // If the sp was not previously discovered
            if (haDomain == null) {
                haDomain = new StorageHADomain();
                haDomain.setId(URIUtil.createId(StorageHADomain.class));
                haDomain.setNativeGuid(adapterNativeGuid);
                haDomain.setStorageDeviceURI(system.getId());
                haDomain.setAdapterName(sp.getId());
                haDomain.setName(sp.getId());
                haDomain.setSerialNumber(sp.getEmcSerialNumber());
                newSPs.add(haDomain);
            } else {
                existingSPs.add(haDomain);
            }
            spIdMap.put(sp.getId(), haDomain.getId());
        }

        _logger.info(
                "Storage processors discovery for storage system {} complete.",
                system.getId());
        for (StorageHADomain newDomain : newSPs) {
            _logger.info("New NasServer : {} : {}", newDomain.getNativeGuid(),
                    newDomain.getId());
        }
        for (StorageHADomain domain : existingSPs) {
            _logger.info("Existing NasServer : {} : {}",
                    domain.getNativeGuid(), domain.getId());
        }

        result.put(NEW, newSPs);
        result.put(EXISTING, existingSPs);
        return result;
    }

    /**
     * Discover iscsiPorts
     * 
     * @param system
     * @param client
     * @param spIdMap
     *            storage processors VNXeId and ViPR URI map
     * @return
     * @throws VNXeException
     */
    private HashMap<String, List<StoragePort>> discoverIscsiPorts(
            StorageSystem system, VNXeApiClient client, Map<String, URI> spIdMap)
            throws VNXeException {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info(
                "Start iSCSI storage port discovery for storage system {}",
                system.getId());
        // Retrieve the list of iscsi ports
        List<VNXeIscsiNode> ports = client.getAllIscsiPorts();
        if (ports == null || ports.isEmpty()) {
            _logger.info("No iSCSI ports found for the system: {} ",
                    system.getId());
            return storagePorts;
        }
        _logger.info("Number iSCSI ports found: {}", ports.size());
        // Create the list of storage ports.
        for (VNXeIscsiNode node : ports) {
            StoragePort port = null;

            VNXeEthernetPort eport = node.getEthernetPort();
            if (eport == null) {
                _logger.info("No ethernet port found for the iscsi node: {}",
                        node.getId());
                continue;
            }
            VNXeBase spId = eport.getStorageProcessorId();
            if (spId == null) {
                _logger.info(
                        "No storage processor info for the iscsi node: {}",
                        node.getId());
                continue;
            }
            String spIdStr = spId.getId();
            URI haDomainUri = spIdMap.get(spIdStr);
            if (haDomainUri == null) {
                _logger.info("The sp {} has not been discovered.", spIdStr);
                continue;
            }

            // Check if storage port was already discovered
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, node.getName(), NativeGUIDGenerator.PORT);

            URIQueryResultList results = new URIQueryResultList();

            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortByNativeGuidConstraint(portNativeGuid),
                    results);

            if (results.iterator().hasNext()) {
                _logger.info("cross verifying for duplicate port");

                StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                        results.iterator().next());

                _logger.info(String
                        .format("Actual StorageDevice %s : PortGroup found for port %s - Actual PortGroup %s",
                                system.getId(), tmpPort.getPortNetworkId(),
                                tmpPort.getPortGroup()));

                if (tmpPort.getStorageDevice().equals(system.getId())
                        && tmpPort.getPortGroup().equals(spIdStr)) {
                    port = tmpPort;
                    _logger.info("found duplicate iscsi port {}",
                            node.getName());
                }
            }

            // If iscsi port was not previously discovered, add new storage port
            if (port == null) {
                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setLabel(portNativeGuid);
                port.setTransportType("IP");
                port.setNativeGuid(portNativeGuid);
                port.setStorageDevice(system.getId());
                port.setRegistrationStatus(RegistrationStatus.REGISTERED
                        .toString());
                port.setPortName(eport.getId());
                port.setPortNetworkId(node.getName());
                port.setPortGroup(spIdStr);
                port.setStorageHADomain(haDomainUri);
                List<Integer> opstatus = eport.getOperationalStatus();
                Integer ok = 2;
                if (opstatus.contains(ok)) {
                    port.setOperationalStatus(StoragePort.OperationalStatus.OK
                            .name());
                } else {
                    port.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK
                            .name());
                }
                VNXeIscsiPortal portal = node.getIscsiPortal();
                if (portal != null) {
                    port.setIpAddress(portal.getIpAddress());
                } else {
                    port.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK
                            .name());
                }
                _logger.info(
                        "Creating new storage port using NativeGuid : {}, IQN:",
                        portNativeGuid, node.getName());
                newStoragePorts.add(port);
            } else {
                existingStoragePorts.add(port);
            }
            port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        }

        _logger.info("iSCSI port discovery for storage system {} complete",
                system.getId());

        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        return storagePorts;
    }

    /**
     * Discover fcPorts
     * 
     * @param system
     * @param client
     * @param spIdMap
     *            storage processors VNXeId and ViPR URI map
     * @return
     * @throws VNXeException
     */
    private HashMap<String, List<StoragePort>> discoverFcPorts(
            StorageSystem system, VNXeApiClient client, Map<String, URI> spIdMap)
            throws VNXeException {

        HashMap<String, List<StoragePort>> storagePorts = new HashMap<String, List<StoragePort>>();

        List<StoragePort> newStoragePorts = new ArrayList<StoragePort>();
        List<StoragePort> existingStoragePorts = new ArrayList<StoragePort>();

        _logger.info("Start FC storage port discovery for storage system {}",
                system.getId());

        // Retrieve the list of iscsi ports
        List<VNXeFCPort> ports = client.getAllFcPorts();
        if (ports == null || ports.isEmpty()) {
            _logger.info("No FC ports found for the system: {} ",
                    system.getId());
            storagePorts.put(NEW, newStoragePorts);
            storagePorts.put(EXISTING, existingStoragePorts);
            return storagePorts;
        }
        // Create the list of storage ports.
        for (VNXeFCPort fcPort : ports) {
            StoragePort port = null;

            VNXeBase spId = fcPort.getStorageProcessorId();
            if (spId == null) {
                _logger.info("No storage processor info for the fcPort: {}",
                        fcPort.getId());
                continue;
            }
            String spIdStr = spId.getId();
            URI haDomainUri = spIdMap.get(spIdStr);
            if (haDomainUri == null) {
                _logger.info("The sp {} has not been discovered.", spIdStr);
                continue;
            }

            // Check if storage port was already discovered
            String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                    system, fcPort.getWwn(), NativeGUIDGenerator.PORT);

            URIQueryResultList results = new URIQueryResultList();

            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStoragePortByNativeGuidConstraint(portNativeGuid),
                    results);

            if (results.iterator().hasNext()) {
                _logger.debug("cross verifying for duplicate port");

                StoragePort tmpPort = _dbClient.queryObject(StoragePort.class,
                        results.iterator().next());

                _logger.info(String
                        .format("Actual StorageDevice %s : PortGroup found for port %s - Actual PortGroup %s",
                                system.getId(), tmpPort.getPortNetworkId(),
                                tmpPort.getPortGroup()));

                if (tmpPort.getStorageDevice().equals(system.getId())
                        && tmpPort.getPortGroup().equals(spIdStr)) {
                    port = tmpPort;
                    _logger.debug("found duplicate fc port {}", fcPort.getWwn());
                }
            }

            // If the fc port was not previously discovered, add new storage
            // port
            if (port == null) {
                port = new StoragePort();
                port.setId(URIUtil.createId(StoragePort.class));
                port.setLabel(portNativeGuid);
                port.setTransportType("FC");
                port.setNativeGuid(portNativeGuid);
                port.setStorageDevice(system.getId());
                port.setRegistrationStatus(RegistrationStatus.REGISTERED
                        .toString());
                port.setPortName(fcPort.getId());
                port.setPortNetworkId(fcPort.getPortWwn());
                port.setPortGroup(spIdStr);
                port.setStorageHADomain(haDomainUri);
                List<Integer> opstatus = fcPort.getOperationalStatus();
                Integer ok = 2;
                if (opstatus.contains(ok)) {
                    port.setOperationalStatus(StoragePort.OperationalStatus.OK
                            .name());
                } else {
                    port.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK
                            .name());
                }

                _logger.info(
                        "Creating new storage port using NativeGuid : {}, WWN:",
                        portNativeGuid, fcPort.getWwn());
                newStoragePorts.add(port);
            } else {
                existingStoragePorts.add(port);
            }
            port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
            port.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
        }

        _logger.info("FC port discovery for storage system {} complete",
                system.getId());

        storagePorts.put(NEW, newStoragePorts);
        storagePorts.put(EXISTING, existingStoragePorts);
        return storagePorts;
    }

    private HashMap<String, List<StorageTier>> discoverStorageTier(
            StorageSystem system, VNXeApiClient client) throws VNXeException {

        HashMap<String, List<StorageTier>> storageTiers = new HashMap<String, List<StorageTier>>();

        List<StorageTier> newTiers = new ArrayList<StorageTier>();
        List<StorageTier> existingTiers = new ArrayList<StorageTier>();

        List<VNXeStorageTier> tiers = client.getStorageTiers();
        String systemNativeGuid = NativeGUIDGenerator
                .generateNativeGuid(system);
        for (VNXeStorageTier tier : tiers) {
            String nativeId = tier.getId();
            StorageTier tierObj = null;

            String tierNativeGuid = NativeGUIDGenerator
                    .generateStorageTierNativeGuidForVmaxTier(systemNativeGuid,
                            nativeId);
            URIQueryResultList results = new URIQueryResultList();

            _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                    .getStorageTierByIdConstraint(tierNativeGuid), results);
            if (results.iterator().hasNext()) {
                _logger.info("Getting the storage tier.");

                StorageTier tmpTier = _dbClient.queryObject(StorageTier.class,
                        results.iterator().next());

                _logger.info(String.format(
                        "Actual StorageDevice %s : storage tier : %s",
                        system.getId(), tmpTier.getNativeGuid()));

                tierObj = tmpTier;
            }
            boolean isNewTier = false;
            if (null == tierObj) {
                tierObj = new StorageTier();
                tierObj.setId(URIUtil.createId(StorageTier.class));
                tierObj.setNativeGuid(tierNativeGuid);
                isNewTier = true;
            }

            tierObj.setLabel(tier.getName());
            tierObj.setTotalCapacity(VNXeUtils.convertDoubleSizeToViPRLong(tier.getSizeTotal()));
            if (isNewTier) {
                newTiers.add(tierObj);
            } else {
                existingTiers.add(tierObj);
            }
        }
        storageTiers.put(NEW, newTiers);
        storageTiers.put(EXISTING, existingTiers);

        return storageTiers;

    }

    private HashMap<String, List<AutoTieringPolicy>> discoverAutoTierPolicies(
            StorageSystem system, VNXeApiClient apiClient) {
        HashMap<String, List<AutoTieringPolicy>> policies = new HashMap<String, List<AutoTieringPolicy>>();

        List<AutoTieringPolicy> newPolicies = new ArrayList<AutoTieringPolicy>();
        List<AutoTieringPolicy> updatePolicies = new ArrayList<AutoTieringPolicy>();
        String[] tierPolicies = apiClient.getAutoTierPolicies();
        String systemNativeGuid = NativeGUIDGenerator
                .generateNativeGuid(system);
        for (String policyName : tierPolicies) {
            String policyNativeGuid = NativeGUIDGenerator
                    .generateAutoTierPolicyNativeGuid(systemNativeGuid,
                            policyName, NativeGUIDGenerator.FASTPOLICY);
            URIQueryResultList result = new URIQueryResultList();
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getAutoTieringPolicyByNativeGuidConstraint(policyNativeGuid),
                    result);
            AutoTieringPolicy policy = null;
            if (result.iterator().hasNext()) {
                policy = _dbClient.queryObject(AutoTieringPolicy.class, result
                        .iterator().next());
            }
            boolean newPolicy = false;
            if (null == policy) {
                newPolicy = true;
                policy = new AutoTieringPolicy();
                policy.setId(URIUtil.createId(AutoTieringPolicy.class));
                policy.setStorageSystem(system.getId());
                policy.setNativeGuid(policyNativeGuid);
                policy.setSystemType(system.getSystemType());
            }

            policy.setLabel(policyName);
            policy.setPolicyName(policyName);
            policy.setPolicyEnabled(true);
            policy.setProvisioningType(AutoTieringPolicy.ProvisioningType.All
                    .name());
            if (newPolicy) {
                newPolicies.add(policy);
            } else {
                updatePolicies.add(policy);
            }

        }
        policies.put(NEW, newPolicies);
        policies.put(EXISTING, updatePolicies);
        return policies;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws VNXeException {

        _logger.info("Start collecting statistics for ip address {}",
                accessProfile.getIpAddress());

        _logger.info("End collecting statistics for ip address {}",
                accessProfile.getIpAddress());

    }

    private boolean getPoolAutoTieringEnabled(VNXePool vnxePool,
            StorageSystem system) {
        boolean enabled = false;
        if (system.getAutoTieringEnabled()) {
            List<PoolTier> tiers = vnxePool.getTiers();
            int numOfTiers = 0;
            for (PoolTier tier : tiers) {
                if (tier.getSizeTotal() > 0) {
                    numOfTiers++;
                }
            }
            if (numOfTiers > 1) {
                enabled = true;
            } else {
                enabled = false;
            }
        }
        return enabled;
    }
}
