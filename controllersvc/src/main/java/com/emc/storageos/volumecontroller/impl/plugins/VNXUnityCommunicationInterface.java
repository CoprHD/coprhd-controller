/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.plugins;

import static com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator.generateNativeGuid;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.CifsServerMap;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.NasCifsServer;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.RegistrationStatus;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StorageProtocol;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.vnxe.VNXeApiClient;
import com.emc.storageos.vnxe.VNXeApiClientFactory;
import com.emc.storageos.vnxe.VNXeException;
import com.emc.storageos.vnxe.VNXeUtils;
import com.emc.storageos.vnxe.models.BasicSystemInfo;
import com.emc.storageos.vnxe.models.Disk;
import com.emc.storageos.vnxe.models.DiskGroup;
import com.emc.storageos.vnxe.models.Health;
import com.emc.storageos.vnxe.models.PoolTier;
import com.emc.storageos.vnxe.models.RaidGroup;
import com.emc.storageos.vnxe.models.RaidTypeEnum;
import com.emc.storageos.db.client.model.VirtualNAS;
import com.emc.storageos.vnxe.models.VNXeBase;
import com.emc.storageos.db.client.model.VirtualNAS.VirtualNasState;
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
import com.emc.storageos.volumecontroller.impl.vnxunity.VNXUnityUnManagedObjectDiscoverer;

/**
 * VNXUnityCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to discover for VNXUnity using
 * ThunderBird API.
 * 
 */
public class VNXUnityCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {
    private static final Logger _logger = LoggerFactory
            .getLogger(VNXUnityCommunicationInterface.class);
    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private static final int LOCK_WAIT_SECONDS = 300;

    // Reference to the VNX unity client factory allows us to get a VNX unity client
    // and execute requests to the VNX Unity storage system.
    private VNXeApiClientFactory clientFactory;
    private VNXUnityUnManagedObjectDiscoverer unityUnManagedObjectDiscoverer;

    public VNXUnityCommunicationInterface() {
    };

    public VNXeApiClientFactory geClientFactory() {
        return clientFactory;
    }

    public void setClientFactory(VNXeApiClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public void setUnManagedObjectDiscoverer(
            VNXUnityUnManagedObjectDiscoverer volumeDiscoverer) {
        this.unityUnManagedObjectDiscoverer = volumeDiscoverer;
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
        _logger.info("Starting scan of Unity StorageProvider. IP={}", accessProfile.getIpAddress());
        StorageProvider.ConnectionStatus cxnStatus = StorageProvider.ConnectionStatus.CONNECTED;
        StorageProvider provider = _dbClient.queryObject(StorageProvider.class, accessProfile.getSystemId());

        _locker.acquireLock(accessProfile.getIpAddress(), LOCK_WAIT_SECONDS);
        try {
            VNXeApiClient apiClient = getVnxUnityClient(accessProfile);
            if (apiClient != null) {
                Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();
                BasicSystemInfo unitySystem = apiClient.getBasicSystemInfo();
                ;

                String unityType = StorageSystem.Type.unity.name();
                String version = unitySystem.getApiVersion();
                String compatibility = StorageSystem.CompatibilityStatus.COMPATIBLE.name();
                provider.setCompatibilityStatus(compatibility);
                provider.setVersionString(version);
                VNXeStorageSystem system = apiClient.getStorageSystem();

                _logger.info("Found Unity: {} ", system.getSerialNumber());
                String id = system.getSerialNumber();
                String nativeGuid = generateNativeGuid(unityType, id);
                StorageSystemViewObject viewObject = storageSystemsCache.get(nativeGuid);
                if (viewObject == null) {
                    viewObject = new StorageSystemViewObject();
                }
                viewObject.setDeviceType(unityType);
                viewObject.addprovider(accessProfile.getSystemId().toString());
                viewObject.setProperty(StorageSystemViewObject.MODEL, unitySystem.getModel());
                viewObject.setProperty(StorageSystemViewObject.SERIAL_NUMBER, id);
                storageSystemsCache.put(nativeGuid, viewObject);

            }
        } catch (Exception e) {
            cxnStatus = StorageProvider.ConnectionStatus.NOTCONNECTED;
            _logger.error(String.format("Exception was encountered when attempting to scan Unity Instance %s",
                    accessProfile.getIpAddress()), e);
            throw VNXeException.exceptions.scanFailed(accessProfile.getIpAddress(), e);
        } finally {
            provider.setConnectionStatus(cxnStatus.name());
            _dbClient.updateObject(provider);
            _logger.info("Completed scan of Unity StorageProvider. IP={}", accessProfile.getIpAddress());
            _locker.releaseLock(accessProfile.getIpAddress());
        }
    }

    /**
     * Implementation for VNX Unity storage systems discovery, both of block and file
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
            _logger.info("Access Profile Details :  IpAddress : {}, PortNumber : {}", accessProfile.getIpAddress(),
                    accessProfile.getPortNumber());
            if (null != accessProfile.getnamespace() && (accessProfile.getnamespace()
                    .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString())
                    || accessProfile.getnamespace()
                            .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS.toString()))) {
                discoverUnmanagedObjects(accessProfile);
            } else {
                // Get the VNX Unity storage system from the database.
                viprStorageSystem = _dbClient.queryObject(StorageSystem.class, storageSystemURI);

                _logger.info(String.format("Discover VnxUnity storage system %s at IP:%s, PORT:%s",
                        storageSystemURI.toString(), accessProfile.getIpAddress(), accessProfile.getPortNumber()));

                // Get the vnx unity service client for getting information
                // about the Vnx Unity storage system.
                VNXeApiClient client = getVnxUnityClient(accessProfile);
                _logger.debug("Got handle to Vnx unity service client");

                // Get the serial number and the native guid and set into the
                // storage system.
                _logger.info("Discovering storage system properties.");

                VNXeStorageSystem system = client.getStorageSystem();
                boolean isFASTVPEnabled = false;
                if (system != null) {
                    viprStorageSystem.setSerialNumber(system.getSerialNumber());

                    String guid = NativeGUIDGenerator.generateNativeGuid(viprStorageSystem);
                    viprStorageSystem.setNativeGuid(guid);

                    viprStorageSystem.setLabel(guid);

                    viprStorageSystem
                            .setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());

                    viprStorageSystem.setReachableStatus(true);

                    isFASTVPEnabled = client.isFASTVPEnabled();
                    viprStorageSystem.setAutoTieringEnabled(isFASTVPEnabled);

                    StringSet supportedActions = new StringSet();
                    supportedActions.add(StorageSystem.AsyncActions.CreateElementReplica.name());
                    supportedActions.add(StorageSystem.AsyncActions.CreateGroupReplica.name());
                    viprStorageSystem.setSupportedAsynchronousActions(supportedActions);

                    StringSet supportedReplica = new StringSet();
                    supportedReplica.add(StorageSystem.SupportedReplicationTypes.LOCAL.name());
                    viprStorageSystem.setSupportedReplicationTypes(supportedReplica);

                    viprStorageSystem.setSupportSoftLimit(true);
                    viprStorageSystem.setIpAddress(accessProfile.getIpAddress());
                    viprStorageSystem.setUsername(accessProfile.getUserName());
                    viprStorageSystem.setPortNumber(accessProfile.getPortNumber());
                    viprStorageSystem.setPassword(accessProfile.getPassword());
                    _dbClient.updateObject(viprStorageSystem);
                    _completer.statusPending(_dbClient, "Completed discovery of system properties");
                } else {
                    _logger.error("Failed to retrieve VNX Unity system info!");
                    viprStorageSystem.setReachableStatus(false);
                }
                // get version for the storage system
                BasicSystemInfo info = client.getBasicSystemInfo();
                if (info != null) {
                    viprStorageSystem.setFirmwareVersion(info.getSoftwareVersion());
                }
                StringSet arraySupportedProtocols = new StringSet();
                // Discover the NasServers
                Map<String, URI> nasServerIdMap = new HashMap<String, URI>();
                Map<String, List<StorageHADomain>> nasServers = discoverNasServers(viprStorageSystem, client,
                        nasServerIdMap, arraySupportedProtocols);
                _logger.info("No of newly discovered NasServers {}", nasServers.get(NEW).size());
                _logger.info("No of existing discovered NasServers {}", nasServers.get(EXISTING).size());

                if (!nasServers.get(NEW).isEmpty()) {
                    _dbClient.createObject(nasServers.get(NEW));
                }

                if (!nasServers.get(EXISTING).isEmpty()) {
                    _dbClient.updateObject(nasServers.get(EXISTING));
                }
                _completer.statusPending(_dbClient, "Completed NAS Server discovery");

                // Discover FileInterfaces
                List<StoragePort> allExistingPorts = new ArrayList<StoragePort>();
                List<StoragePort> allNewPorts = new ArrayList<StoragePort>();
                Map<String, List<StoragePort>> ports = discoverFileStoragePorts(viprStorageSystem, client,
                        nasServerIdMap);

                if (ports.get(NEW) != null && !ports.get(NEW).isEmpty()) {
                    allNewPorts.addAll(ports.get(NEW));
                    _dbClient.createObject(ports.get(NEW));
                }

                if (ports.get(EXISTING) != null && !ports.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(ports.get(EXISTING));
                    _dbClient.updateObject(ports.get(EXISTING));
                }
                _completer.statusPending(_dbClient, "Completed file ports discovery");

                // discover storage processors
                Map<String, URI> spIdMap = new HashMap<String, URI>();
                Map<String, List<StorageHADomain>> sps = discoverStorageProcessors(viprStorageSystem, client, spIdMap);

                if (!sps.get(NEW).isEmpty()) {
                    _dbClient.createObject(sps.get(NEW));
                }

                if (!sps.get(EXISTING).isEmpty()) {
                    _dbClient.updateObject(sps.get(EXISTING));
                }
                _completer.statusPending(_dbClient, "Completed storage processor discovery");

                // discover iscsi ports
                Map<String, List<StoragePort>> iscsiPorts = discoverIscsiPorts(viprStorageSystem, client, spIdMap);
                boolean hasIscsiPorts = false;
                if (iscsiPorts.get(NEW) != null && !iscsiPorts.get(NEW).isEmpty()) {
                    allNewPorts.addAll(iscsiPorts.get(NEW));
                    hasIscsiPorts = true;
                    _dbClient.createObject(iscsiPorts.get(NEW));
                }

                if (iscsiPorts.get(EXISTING) != null && !iscsiPorts.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(iscsiPorts.get(EXISTING));
                    hasIscsiPorts = true;
                    _dbClient.updateObject(iscsiPorts.get(EXISTING));
                }
                if (hasIscsiPorts) {
                    arraySupportedProtocols.add(StorageProtocol.Block.iSCSI.name());
                }
                _completer.statusPending(_dbClient, "Completed iscsi ports discovery");

                // discover fc ports
                Map<String, List<StoragePort>> fcPorts = discoverFcPorts(viprStorageSystem, client, spIdMap);
                boolean hasFcPorts = false;
                if (fcPorts.get(NEW) != null && !fcPorts.get(NEW).isEmpty()) {
                    allNewPorts.addAll(fcPorts.get(NEW));
                    hasFcPorts = true;
                    _dbClient.createObject(fcPorts.get(NEW));
                }

                if (fcPorts.get(EXISTING) != null && !fcPorts.get(EXISTING).isEmpty()) {
                    allExistingPorts.addAll(fcPorts.get(EXISTING));
                    hasFcPorts = true;
                    _dbClient.updateObject(fcPorts.get(EXISTING));
                }
                if (hasFcPorts) {
                    arraySupportedProtocols.add(StorageProtocol.Block.FC.name());
                }
                _completer.statusPending(_dbClient, "Completed FC ports discovery");

                List<StoragePort> allPorts = new ArrayList<StoragePort>(allNewPorts);
                allPorts.addAll(allExistingPorts);
                // check if any port not visible in this discovery
                List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts, _dbClient,
                        viprStorageSystem.getId());
                if (notVisiblePorts != null && !notVisiblePorts.isEmpty()) {
                    allExistingPorts.addAll(notVisiblePorts);
                }
                /**
                 * Discover the VNX Unity pool information.
                 */
                _logger.info("Discovering storage pools.");
                List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
                List<StoragePool> allPools = new ArrayList<StoragePool>();
                Map<String, List<StoragePool>> pools = discoverStoragePools(viprStorageSystem, client,
                        arraySupportedProtocols, poolsToMatchWithVpool);

                _logger.info("No of newly discovered pools {}", pools.get(NEW).size());
                _logger.info("No of existing discovered pools {}", pools.get(EXISTING).size());
                if (!pools.get(NEW).isEmpty()) {
                    allPools.addAll(pools.get(NEW));
                    _dbClient.createObject(pools.get(NEW));
                    StoragePoolAssociationHelper.setStoragePoolVarrays(viprStorageSystem.getId(), pools.get(NEW),
                            _dbClient);
                }

                if (!pools.get(EXISTING).isEmpty()) {
                    allPools.addAll(pools.get(EXISTING));
                    _dbClient.updateObject(pools.get(EXISTING));
                }

                List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(allPools, _dbClient,
                        viprStorageSystem.getId());
                if (notVisiblePools != null && !notVisiblePools.isEmpty()) {
                    poolsToMatchWithVpool.addAll(notVisiblePools);
                }

                StoragePortAssociationHelper.runUpdatePortAssociationsProcess(allNewPorts, allExistingPorts, _dbClient,
                        _coordinator, poolsToMatchWithVpool);
                _completer.statusPending(_dbClient, "Completed pool discovery");

                // This associates the VNas with the virtual array
                StoragePortAssociationHelper.runUpdateVirtualNasAssociationsProcess(allExistingPorts, null, _dbClient);
                _logger.info("update virtual nas association for unity");
                /**
                 * Discover AutoTieringPolicies and StorageTiers if FASTVP
                 * enabled.
                 */

                if (isFASTVPEnabled) {
                    _logger.info("FASTVP is enabled");
                    HashMap<String, List<AutoTieringPolicy>> policies = discoverAutoTierPolicies(viprStorageSystem,
                            client);
                    if (!policies.get(NEW).isEmpty()) {
                        _dbClient.createObject(policies.get(NEW));
                    }

                    if (!policies.get(EXISTING).isEmpty()) {
                        _dbClient.updateObject(policies.get(EXISTING));
                    }

                    HashMap<String, List<StorageTier>> tiers = discoverStorageTier(viprStorageSystem, client);
                    if (!tiers.get(NEW).isEmpty()) {
                        _dbClient.createObject(tiers.get(NEW));
                    }

                    if (!tiers.get(EXISTING).isEmpty()) {
                        _dbClient.updateObject(tiers.get(EXISTING));
                    }
                }
                detailedStatusMessage = String.format("Discovery completed successfully for Storage System: %s",
                        storageSystemURI.toString());

            }

        } catch (Exception e) {
            detailedStatusMessage = String.format("Discovery failed for VNX Unity %s: %s", storageSystemURI.toString(),
                    e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw VNXeException.exceptions.discoveryError("Discovery error", e);
        } finally {
            if (viprStorageSystem != null) {
                try {
                    // set detailed message
                    viprStorageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(viprStorageSystem);
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

            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString());
            _dbClient.updateObject(storageSystem);
            if (accessProfile.getnamespace().equals(StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS.toString())) {
                unityUnManagedObjectDiscoverer.discoverUnManagedFileSystems(accessProfile, _dbClient, _coordinator, _partitionManager);
                unityUnManagedObjectDiscoverer.discoverAllExportRules(accessProfile, _dbClient, _partitionManager);
                unityUnManagedObjectDiscoverer.discoverAllCifsShares(accessProfile, _dbClient, _partitionManager);
                unityUnManagedObjectDiscoverer.discoverAllTreeQuotas(accessProfile, _dbClient, _partitionManager);

            } else if (accessProfile.getnamespace().equals(
                    StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString())) {
                unityUnManagedObjectDiscoverer.discoverUnManagedVolumes(accessProfile, _dbClient, _coordinator, _partitionManager);
            }

            // discovery succeeds
            detailedStatusMessage = String.format("UnManaged Object Discovery completed successfully for VNX Unity: %s",
                    storageSystem.getId().toString());
            _logger.info(detailedStatusMessage);

        } catch (Exception e) {
            detailedStatusMessage = String
                    .format("Discovery of unmanaged volumes failed for system %s because %s", storageSystem.getId().toString(),
                            e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw VNXeException.exceptions.discoveryError("Unmanaged objectobject discovery error", e);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem.setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.updateObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error("Error while updating unmanaged object discovery status for system.", ex);
                }
            }
        }
    }

    /**
     * Get the Vnx Unity service client for making requests to the VNX Unity based on the
     * passed profile.
     * 
     * @param accessProfile
     *            A reference to the access profile.
     * 
     * @return A reference to the Vnx unity service client.
     */
    private VNXeApiClient getVnxUnityClient(AccessProfile accessProfile) {
        VNXeApiClient client = clientFactory.getUnityClient(
                accessProfile.getIpAddress(), accessProfile.getPortNumber(),
                accessProfile.getUserName(), accessProfile.getPassword());

        return client;

    }

    /**
     * Returns the list of storage pools for the specified VNX Unity storage system.
     * 
     * @param system
     *            storage system information.
     * @param client
     *            VNX Unity service client
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
                    Health poolHealth = vnxePool.getHealth();
                    if (poolHealth != null) {
                        int value = poolHealth.getValue();
                        if (value == Health.HealthEnum.OK.getValue()|| 
                                value == Health.HealthEnum.OK_BUT.getValue()) {
                            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
                        } else {
                            pool.setOperationalStatus(StoragePool.PoolOperationalStatus.NOTREADY.name());
                        }
                    }
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
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
                    // pool.setOperationalStatus(vnxePool.getStatus());
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY.name());
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
                if (poolTiers != null) {
                    for (PoolTier poolTier : poolTiers) {

                        List<RaidGroup> raidGroups = poolTier.getRaidGroups();
                        if (raidGroups != null) {
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
                    }
                }
                // Get drive types from disks
                List<Disk> disks = client.getDisksForPool(vnxePool.getId());
                if (disks != null) {
                    for (Disk disk : disks) {
                        diskTypes.add(disk.getDiskTechnologyEnum().name());
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

        List<VirtualNAS> newVirtualNas = new ArrayList<VirtualNAS>();
        List<VirtualNAS> existingVirtualNas = new ArrayList<VirtualNAS>();

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
            if ((nasServer.getMode() == VNXeNasServer.NasServerModeEnum.DESTINATION) || (nasServer.getIsReplicationDestination() == true)) {
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
            String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(system, nasServer.getName(),
                    NativeGUIDGenerator.ADAPTER);
            _dbClient.queryByConstraint(
                    AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid), results);

            if (results.iterator().hasNext()) {
                StorageHADomain tmpDomain = _dbClient.queryObject(StorageHADomain.class, results.iterator().next());

                if (tmpDomain.getStorageDeviceURI().equals(system.getId())) {
                    haDomain = tmpDomain;
                    _logger.debug("Found duplicate {} ", nasServer.getName());
                }
            }
            // get the supported protocol on the nasServer
            if (cifsServers != null && !cifsServers.isEmpty()) {
                for (VNXeCifsServer cifsServer : cifsServers) {
                    if (cifsServer.getNasServer().getId().equals(nasServer.getId())) {
                        protocols.add(StorageProtocol.File.CIFS.name());
                        isCIFSSupported = true;
                        break;
                    }
                }
            }

            if (nfsServers != null && !nfsServers.isEmpty()) {
                for (VNXeNfsServer nfsServer : nfsServers) {
                    if (nfsServer.getNasServer() != null) {
                        if (nfsServer.getNasServer().getId()
                                .equals(nasServer.getId())) {
                            protocols.add(StorageProtocol.File.NFS.name());
                            isNFSSupported = true;
                            break;
                        }
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
                haDomain.setVirtual(true);
                newNasServers.add(haDomain);

            } else {
                haDomain.setFileSharingProtocols(protocols);
                haDomain.setVirtual(true);
                existingNasServers.add(haDomain);
            }
            nasServerIdMap.put(nasServer.getId(), haDomain.getId());

            CifsServerMap cifsServersMap = new CifsServerMap();

            for (VNXeCifsServer cifsServer : cifsServers) {
                if (cifsServer.getNasServer().getId().equals(nasServer.getId())) {
                    _logger.info("Cifs Server {} for {} ", cifsServer.getName(), nasServer.getName());
                    if (!cifsServer.getFileInterfaces().isEmpty()) {
                        _logger.info("{} has CIFS Enabled since interfaces are found ", nasServer.getName(),
                                cifsServer.getName() + ":" + cifsServer.getFileInterfaces());
                        protocols.add(StorageProtocol.File.CIFS.name());

                        NasCifsServer nasCifsServer = new NasCifsServer();
                        nasCifsServer.setId(cifsServer.getId());
                        // nasCifsServer.setInterfaces(cifsServer.getFileInterfaces()); TODO
                        nasCifsServer.setMoverIdIsVdm(true);
                        nasCifsServer.setName(cifsServer.getName());
                        // nasCifsServer.setType(cifsServer.getType());
                        nasCifsServer.setDomain(cifsServer.getDomain());
                        cifsServersMap.put(cifsServer.getName(), nasCifsServer);
                    }
                }
            }

            VirtualNAS vNas = findvNasByNativeId(system, nasServer.getId());

            // If the nasServer was not previously discovered
            if (vNas == null) {
                vNas = new VirtualNAS();
                vNas.setId(URIUtil.createId(VirtualNAS.class));
                String nasNativeGuid = NativeGUIDGenerator.generateNativeGuid(system, nasServer.getId(), NativeGUIDGenerator.VIRTUAL_NAS);
                vNas.setNativeGuid(nasNativeGuid);
                vNas.setStorageDeviceURI(system.getId());
                vNas.setNasName(nasServer.getName());
                vNas.setNativeId(nasServer.getId());
                vNas.setNasState(VirtualNasState.LOADED.name());
                vNas.setProtocols(protocols);
                vNas.setCifsServersMap(cifsServersMap);
                newVirtualNas.add(vNas);
            } else {
                vNas.setProtocols(protocols);
                vNas.setNasState(VirtualNasState.LOADED.name());
                vNas.setCifsServersMap(cifsServersMap);
                existingVirtualNas.add(vNas);
            }

        }

        // Persist the NAS servers!!!
        if (existingVirtualNas != null && !existingVirtualNas.isEmpty()) {
            _logger.info("discoverNasServers - modified VirtualNAS servers size {}", existingVirtualNas.size());
            _dbClient.updateObject(existingVirtualNas);
        }

        if (newVirtualNas != null && !newVirtualNas.isEmpty()) {
            _logger.info("discoverNasServers - new VirtualNAS servers size {}", newVirtualNas.size());
            _dbClient.createObject(newVirtualNas);
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

        List<VirtualNAS> modifiedServers = new ArrayList<VirtualNAS>();

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

            // Associate Storage Port to Virtual NAS

            VirtualNAS vNas = findvNasByNativeId(system, nasServerId);
            if (vNas != null) {
                if (vNas.getStoragePorts() != null && !vNas.getStoragePorts().isEmpty()) {
                    if (vNas.getStoragePorts().contains(port.getId())) {
                        vNas.getStoragePorts().remove(port.getId());
                    }
                }
                vNas.getStoragePorts().add(port.getId().toString());
                modifiedServers.add(vNas);
                _logger.info("VirtualNAS : {} : port : {} got modified", vNas.getId(), port.getPortName());
            }
        }

        // Persist the modified virtual nas servers
        if (modifiedServers != null && !modifiedServers.isEmpty()) {
            _logger.info("Modified VirtualNAS servers size {}", modifiedServers.size());
            _dbClient.updateObject(modifiedServers);
        }

        _logger.info("Storage port discovery for storage system {} complete",
                system.getId());

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
            _logger.info("New storage processor : {} : {}", newDomain.getNativeGuid(),
                    newDomain.getId());
        }
        for (StorageHADomain domain : existingSPs) {
            _logger.info("Existing storage processor : {} : {}",
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
            VNXeBase spId = eport.getStorageProcessor();
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

                VNXeIscsiPortal portal = node.getIscsiPortal();
                if (portal != null) {
                    port.setIpAddress(portal.getIpAddress());
                }
                _logger.info(
                        "Creating new storage port using NativeGuid : {}, IQN:",
                        portNativeGuid, node.getName());
                newStoragePorts.add(port);
            } else {
                existingStoragePorts.add(port);
            }
            Health health = node.getEthernetPort().getHealth();
            if (health != null && health.getValue() == Health.HealthEnum.OK.getValue()) {
                port.setOperationalStatus(StoragePort.OperationalStatus.OK
                        .name());
            } else {
                port.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK
                        .name());
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

            VNXeBase spId = fcPort.getStorageProcessor();
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

                _logger.info(
                        "Creating new storage port using NativeGuid : {}, WWN:",
                        portNativeGuid, fcPort.getWwn());
                newStoragePorts.add(port);
            } else {
                existingStoragePorts.add(port);
            }
            Health portHealth = fcPort.getHealth();
            if (portHealth != null) {
                int healthValue = portHealth.getValue();
                if (healthValue == Health.HealthEnum.OK.getValue()) {
                    port.setOperationalStatus(StoragePort.OperationalStatus.OK.name());
                } else {
                    port.setOperationalStatus(StoragePort.OperationalStatus.NOT_OK.name());
                }
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

    /**
     * Discover StorageTiers
     * @param system
     * @param client
     * @return
     * @throws VNXeException
     */
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

            tierObj.setLabel(tier.getId());
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

    /**
     * Discover autoTier policies
     * @param system
     * @param apiClient
     * @return
     */
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

    /**
     * Check if the pool's autotiering is enabled. if there are more than one tier in the pool, then it is enabled.
     * @param vnxePool
     * @param system
     * @return true or false
     */
    private boolean getPoolAutoTieringEnabled(VNXePool vnxePool,
            StorageSystem system) {
        boolean enabled = false;
        if (system.getAutoTieringEnabled()) {
            List<PoolTier> tiers = vnxePool.getTiers();
            int numOfTiers = 0;
            if (tiers != null && !tiers.isEmpty()) {
                for (PoolTier tier : tiers) {
                    if (tier.getSizeTotal() > 0) {
                        numOfTiers++;
                    }
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
