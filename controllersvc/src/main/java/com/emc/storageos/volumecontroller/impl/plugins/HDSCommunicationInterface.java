/*
 * Copyright (c) 2008-2013 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.cim.CIMObjectPath;
import javax.security.auth.Subject;
import javax.wbem.WBEMException;
import javax.wbem.client.PasswordCredential;
import javax.wbem.client.UserPrincipal;
import javax.wbem.client.WBEMClient;
import javax.wbem.client.WBEMClientFactory;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

import com.emc.storageos.cimadapter.connections.cim.CimConstants;
import com.emc.storageos.cimadapter.connections.cim.CimObjectPathCreator;
import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.AutoTieringPolicy.HitachiTieringPolicy;
import com.emc.storageos.db.client.model.AutoTieringPolicy.ProvisioningType;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject.CompatibilityStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.DiscoveryStatus;
import com.emc.storageos.db.client.model.DiscoveredDataObject.Type;
import com.emc.storageos.db.client.model.Stat;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StoragePool.PoolServiceType;
import com.emc.storageos.db.client.model.StoragePool.SupportedDriveTypeValues;
import com.emc.storageos.db.client.model.StoragePort;
import com.emc.storageos.db.client.model.StoragePort.OperationalStatus;
import com.emc.storageos.db.client.model.StoragePort.PortType;
import com.emc.storageos.db.client.model.StorageProvider;
import com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.StorageTier;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.db.exceptions.DatabaseException;
import com.emc.storageos.hds.HDSConstants;
import com.emc.storageos.hds.HDSException;
import com.emc.storageos.hds.api.HDSApiClient;
import com.emc.storageos.hds.api.HDSApiFactory;
import com.emc.storageos.hds.model.Pool;
import com.emc.storageos.hds.model.Port;
import com.emc.storageos.hds.model.PortController;
import com.emc.storageos.hds.model.StorageArray;
import com.emc.storageos.hds.model.StoragePoolTier;
import com.emc.storageos.hds.model.TieringPolicy;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.plugins.common.Constants;
import com.emc.storageos.plugins.common.domainmodel.Namespace;
import com.emc.storageos.plugins.common.domainmodel.NamespaceList;
import com.emc.storageos.plugins.metering.smis.SMIPluginException;
import com.emc.storageos.util.VersionChecker;
import com.emc.storageos.volumecontroller.impl.ControllerUtils;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePoolAssociationHelper;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.hds.HDSCollectionException;
import com.emc.storageos.volumecontroller.impl.hds.discovery.HDSVolumeDiscoverer;
import com.emc.storageos.volumecontroller.impl.hds.prov.utils.HDSUtils;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.SMIExecutor;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.PortMetricsProcessor;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;
import com.emc.storageos.volumecontroller.impl.utils.ImplicitPoolMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

/**
 * HDSCommunicationInterface class is an implementation of
 * CommunicationInterface which is responsible to scan the HiCommand device manager.
 * It also does the discovery of pools, adapters & ports.
 * 
 */
public class HDSCommunicationInterface extends ExtendedCommunicationInterfaceImpl {
    private static final String CONTROLLER_HICOMMAND_PROVIDER_VERSION = "controller_hicommand_provider_version";

    /**
     * Logger instance to log messages.
     */
    private static final Logger _logger = LoggerFactory.getLogger(HDSCommunicationInterface.class);

    private static final String COMMA_SEPERATOR = "\\.";

    private HDSApiFactory hdsApiFactory;

    private HDSVolumeDiscoverer volumeDiscoverer;

    private WBEMClient wbemClient = null;

    private NamespaceList namespaces;

    private SMIExecutor executor;

    private PortMetricsProcessor portMetricsProcessor;

    /**
     * @param hdsApiFactory the hdsApiFactory to set
     */
    public void setHdsApiFactory(HDSApiFactory hdsApiFactory) {
        this.hdsApiFactory = hdsApiFactory;
    }

    public void setVolumeDiscoverer(HDSVolumeDiscoverer volumeDiscoverer) {
        this.volumeDiscoverer = volumeDiscoverer;
    }

    public void setNamespaces(NamespaceList namespaces) {
        this.namespaces = namespaces;
    }

    public void setExecutor(SMIExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile)
            throws BaseCollectionException {
        try {
            _logger.info("Access Profile Details :" + accessProfile.toString());
            wbemClient = getCIMClient(accessProfile);
            initMap(accessProfile);
            Namespace namespace = (Namespace) namespaces.getNsList().get(METERING);
            _logger.info("HDS CIMClient initialized successfully");
            executor.setKeyMap(_keyMap);
            executor.execute(namespace);
            dumpStatRecords();
            injectStats();

            // if portMetricsProcesor was injected with metering, trigger pool matcher
            if (portMetricsProcessor != null) {
                //
                // compute port metric to trigger if any port allocation qualification changed. If there is
                // changes, run vpool matcher
                //
                _logger.info("checking to see if Vpool Matcher needs to be run");
                List<StoragePort> systemPorts = ControllerUtils.getSystemPortsOfSystem(_dbClient, accessProfile.getSystemId());
                portMetricsProcessor.triggerVpoolMatcherIfPortAllocationQualificationChanged(accessProfile.getSystemId(), systemPorts);

                //
                // compute storage system's average of port metrics. Then, persist it into storage system object.
                //
                portMetricsProcessor.computeStorageSystemAvgPortMetrics(accessProfile.getSystemId());
            }

        } catch (Exception e) {
            throw new HDSCollectionException(e.getMessage());
        } finally {
            releaseResources();
        }
    }

    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        _logger.info("Scanning started for provider: {}", accessProfile.getSystemId());

        StorageProvider provider = _dbClient.queryObject(StorageProvider.class,
                accessProfile.getSystemId());
        boolean exceptionOccured = false;
        try {
            HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                    HDSUtils.getHDSServerManagementServerInfo(accessProfile),
                    accessProfile.getUserName(), accessProfile.getPassword());

            String apiVersion = hdsApiClient.getProviderAPIVersion();
            _logger.info("Provider {} API Version:{}", provider.getLabel(), apiVersion);
            provider.setVersionString(apiVersion);
            String minimumSupportedVersion = ControllerUtils.getPropertyValueFromCoordinator(_coordinator,
                    CONTROLLER_HICOMMAND_PROVIDER_VERSION);
            if (VersionChecker.verifyVersionDetails(minimumSupportedVersion, apiVersion) < 0)
            {
                provider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                throw new HDSCollectionException(String.format(
                        " ** The HiCommand Device Manager API version is not supported. Minimum supported version should be: %s",
                        minimumSupportedVersion));
            }

            provider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
            List<StorageArray> storageArrayList = hdsApiClient.getStorageSystemsInfo();
            if (null != storageArrayList && !storageArrayList.isEmpty()) {
                _logger.debug("Received proper response from HiCommand server");
                processScanResponse(storageArrayList, accessProfile);
            } else {
                _logger.info("No Systems found during scanning of provider: {}",
                        provider.getId());
            }

        } catch (Exception ex) {
            exceptionOccured = true;
            _logger.error("Exception occurred while scanning provider {}",
                    accessProfile.getSystemId(), ex);
            throw HDSException.exceptions.scanFailed(ex);

        } finally {
            if (exceptionOccured) {
                provider.setConnectionStatus(ConnectionStatus.NOTCONNECTED.name());
            } else {
                provider.setConnectionStatus(ConnectionStatus.CONNECTED.name());
            }
            _dbClient.persistObject(provider);
        }
        _logger.info("Scanning ended for provider: {}", accessProfile.getSystemId());
    }

    /**
     * Process the XMLAPI response and persist storageSystem.
     * 
     * @param result
     */
    private void processScanResponse(List<StorageArray> arrayList,
            AccessProfile accessProfile) {
        _logger.info("{} systems found", arrayList.size());
        Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();
        for (StorageArray array : arrayList) {
            String systemType = StorageSystem.Type.hds.name();
            String model = array.getDisplayArrayType();
            String objectID = array.getObjectID();
            String serialNumber = objectID.split(COMMA_SEPERATOR)[2];
            String arrayFamily = array.getArrayFamily();

            // @TODO Add a model based check for HDS arrays if required.

            StorageSystemViewObject systemVO = null;
            String nativeGuid = NativeGUIDGenerator.generateNativeGuid(systemType,
                    objectID);
            if (storageSystemsCache.containsKey(nativeGuid)) {
                systemVO = storageSystemsCache.get(nativeGuid);
            } else {
                systemVO = new StorageSystemViewObject();
            }
            systemVO.setDeviceType(systemType);
            systemVO.addprovider(accessProfile.getSystemId().toString());
            systemVO.setProperty(StorageSystemViewObject.MODEL, model);
            systemVO.setProperty(StorageSystemViewObject.SERIAL_NUMBER, serialNumber);
            systemVO.setProperty(StorageSystemViewObject.STORAGE_NAME, nativeGuid);

            storageSystemsCache.put(nativeGuid, systemVO);
        }

        _logger.info("Found {} systems during scanning for ip {}",
                storageSystemsCache.size(), accessProfile.getIpAddress());
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {

        if ((null != accessProfile.getnamespace())
                && (accessProfile.getnamespace()
                        .equals(StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES
                                .toString()))) {
            discoverUnManagedVolumes(accessProfile);
        } else {
            _logger.info("Discovery started for system {}", accessProfile.getSystemId());

            StorageSystem storageSystem = null;
            String detailedStatusMessage = "Unknown Status";
            try {
                storageSystem = _dbClient.queryObject(StorageSystem.class,
                        accessProfile.getSystemId());
                HDSApiClient hdsApiClient = hdsApiFactory.getClient(
                        HDSUtils.getHDSServerManagementServerInfo(accessProfile),
                        accessProfile.getUserName(), accessProfile.getPassword());

                // HDS+ARRAY.AMS200.73012495
                Iterable<String> splitter = Splitter.on(HDSConstants.PLUS_OPERATOR)
                        .limit(2).split(storageSystem.getNativeGuid());
                String objectID = Iterables.getLast(splitter);

                StorageArray storageArray = hdsApiClient.getStorageSystemDetails(objectID);

                if (null != storageArray) {
                    parseDiscoveryResponse(storageArray, accessProfile);
                    storageArray = hdsApiClient.getStorageSystemTieringPolicies(objectID);
                    parseDiscoveryTieringPolicyResponse(storageArray, accessProfile);
                    fetchStoragePoolTiers(storageSystem, objectID, accessProfile, hdsApiClient);
                } else {
                    _logger.error(
                            "Discovery failed for system {} as not able to retrieve information from HiCommand DM");
                    throw new HDSCollectionException(
                            "Discovery failed for system as not able to retrieve information from HiCommand Device Manager.");
                }
                // discovery succeeds
                detailedStatusMessage = String.format(
                        "Discovery completed successfully for HDS: %s",
                        accessProfile.getSystemId());
            } catch (Exception e) {
                if (null != storageSystem) {
                    cleanupDiscovery(storageSystem);
                }
                detailedStatusMessage = String.format(
                        "Discovery failed for Storage System: %s because %s",
                        storageSystem.toString(), e.getLocalizedMessage());
                _logger.error(detailedStatusMessage, e);
                throw new HDSCollectionException(detailedStatusMessage);

            } finally {
                try {
                    if (storageSystem != null) {
                        storageSystem
                                .setLastDiscoveryStatusMessage(detailedStatusMessage);
                        _dbClient.persistObject(storageSystem);
                    }
                } catch (Exception e) {
                    _logger.error(e.getMessage(), e);
                }
            }
            _logger.info("Discovery Ended for system {}", accessProfile.getSystemId());
        }
    }

    /**
     * 1. Get all pools & its tiers of a given storagesystem.
     * 2. Check whether pool is HDT (Tiering supported pool or not.)
     * 3. If it is HDT pool, then process all its storage tiers.
     * 
     * @param accessProfile
     * @throws Exception
     */
    private void fetchStoragePoolTiers(StorageSystem system, String systemObjectID,
            AccessProfile accessProfile, HDSApiClient hdsApiClient)
            throws Exception {
        List<Pool> journalPoolList = hdsApiClient
                .getStoragePoolsTierInfo(systemObjectID);
        if (null != journalPoolList && !journalPoolList.isEmpty()) {
            for (Pool pool : journalPoolList) {
                if (null != pool && pool.getTierControl() == 1) {
                    if (null != pool.getTiers() && !pool.getTiers().isEmpty()) {
                        processStoragePoolTiersInfo(system, pool);
                    } else {
                        _logger.debug(
                                "No storage tiers found on journal pool : {}",
                                pool.getObjectID());
                    }
                }
            }
        } else {
            _logger.info("No Journal pools found on storage system {}",
                    systemObjectID);
        }

    }

    private void processStoragePoolTiersInfo(StorageSystem system, Pool pool) {
        String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(system, pool.getObjectID(), NativeGUIDGenerator.POOL);
        URIQueryResultList uriQueryResult = new URIQueryResultList();
        Set<String> tiersSet = new HashSet<String>();
        List<StorageTier> newTiers = new ArrayList<StorageTier>();
        List<StorageTier> updateTiers = new ArrayList<StorageTier>();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStoragePoolByNativeGuidConstraint(poolNativeGuid), uriQueryResult);
        StoragePool poolInDb = null;
        if (uriQueryResult.iterator().hasNext()) {
            poolInDb = _dbClient.queryObject(StoragePool.class, uriQueryResult.iterator().next());
            if (null == poolInDb) {
                return;
            }
        }
        for (StoragePoolTier tier : pool.getTiers()) {
            String tierNativeGuid = NativeGUIDGenerator.generateStorageTierNativeGuidForHDSTier(system, poolInDb.getNativeId(),
                    tier.getTierID());
            StorageTier storageTier = checkStorageTierExistsInDB(tierNativeGuid);
            if (null == storageTier) {
                storageTier = new StorageTier();
                storageTier.setId(URIUtil.createId(StorageTier.class));
                storageTier.setDiskDriveTechnology(tier.getDiskType());
                storageTier.setLabel(tier.getObjectID());
                storageTier.setNativeGuid(tierNativeGuid);
                storageTier.setStorageDevice(system.getId());
                storageTier.setTotalCapacity(Long.parseLong(tier.getCapacityInKB()));
                newTiers.add(storageTier);
            }
            // @TODO check whether we are getting the right percentage.
            storageTier.setPercentage(tier.getUsageRate());
            tiersSet.add(storageTier.getId().toString());
            updateTiers.add(storageTier);
        }
        _dbClient.createObject(newTiers);
        _dbClient.persistObject(updateTiers);
        poolInDb.addTiers(tiersSet);
        _dbClient.persistObject(poolInDb);
    }

    /**
     * Check if Storage Tier exists in DB.
     * 
     * @param tierNativeGuid
     * @param _dbClient
     * @return StorageTier
     * @throws IOException
     */
    private StorageTier checkStorageTierExistsInDB(String tierNativeGuid) {
        StorageTier tier = null;
        URIQueryResultList tierQueryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageTierByIdConstraint(tierNativeGuid), tierQueryResult);
        if (tierQueryResult.iterator().hasNext()) {
            tier = _dbClient.queryObject(StorageTier.class, tierQueryResult.iterator().next());
        }
        return tier;
    }

    /**
     * Discover all UnManagedVolumes for a given storagesystem.
     * 
     * @param accessProfile
     */
    private void discoverUnManagedVolumes(AccessProfile accessProfile) {
        StorageSystem storageSystem = null;
        String detailedStatusMessage = null;
        try {
            storageSystem = _dbClient.queryObject(StorageSystem.class,
                    accessProfile.getSystemId());
            if (null == storageSystem) {
                return;
            }
            volumeDiscoverer.discoverUnManagedVolumes(accessProfile, _dbClient,
                    _coordinator, _partitionManager);
            storageSystem
                    .setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS
                            .toString());
            _dbClient.persistObject(storageSystem);

        } catch (Exception e) {
            if (storageSystem != null) {
                cleanupDiscovery(storageSystem);
            }
            detailedStatusMessage = String.format(
                    "Discovery of unmanaged volumes failed for system %s because %s",
                    storageSystem.getId().toString(), e.getLocalizedMessage());
            _logger.error(detailedStatusMessage, e);
            throw new HDSCollectionException(detailedStatusMessage);
        } finally {
            if (storageSystem != null) {
                try {
                    // set detailed message
                    storageSystem
                            .setLastDiscoveryStatusMessage(detailedStatusMessage);
                    _dbClient.persistObject(storageSystem);
                } catch (Exception ex) {
                    _logger.error(
                            String.format(
                                    "Error while updating unmanaged volume discovery status for system %s",
                                    storageSystem.getId()), ex);
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
                    "discoverStorage failed. Failed to update discovery status to ERROR.",
                    e);
        }

    }

    /**
     * 
     * @param result
     * @param accessProfile
     * @throws Exception
     */
    private void parseDiscoveryResponse(StorageArray array, AccessProfile accessProfile) throws Exception {
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(
                DiscoveredDataObject.Type.hds.toString(), array.getObjectID());
        List<StorageSystem> systems = CustomQueryUtility.getActiveStorageSystemByNativeGuid(_dbClient, nativeGuid);
        if (systems != null && !systems.isEmpty()) {
            try {
                StorageSystem system = systems.get(0);
                processStorageSystemResponse(system, array);
                Set<String> supportedProtocols = new HashSet<String>();
                processStorageAdapterResponse(system, array.getPortControllerList(),
                        accessProfile);
                List<StoragePort> ports = processStoragePortResponse(system, array.getPortList(), accessProfile, supportedProtocols);
                List<StoragePool> poolsToMatchWithVpool = new ArrayList<StoragePool>();
                List<StoragePool> thickPools = processStorageThickPoolResponse(system, array.getThickPoolList(),
                        accessProfile, supportedProtocols, poolsToMatchWithVpool);
                List<StoragePool> thinPools = processStorageThinPoolResponse(system, array.getThinPoolList(),
                        accessProfile, supportedProtocols, poolsToMatchWithVpool);

                // Get the pools not visible in the present discovery
                List<StoragePool> discoveredPools = new ArrayList<StoragePool>(thickPools);
                discoveredPools.addAll(thinPools);
                List<StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(discoveredPools, _dbClient, system.getId());
                for (StoragePool notVisiblePool : notVisiblePools) {
                    poolsToMatchWithVpool.add(notVisiblePool);
                }

                // Get all the ports not visible in the present discovery
                List<StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(ports, _dbClient, system.getId());

                List<StoragePort> updatedPorts = new ArrayList<StoragePort>(ports);
                updatedPorts.addAll(notVisiblePorts);

                StoragePortAssociationHelper.runUpdatePortAssociationsProcess(updatedPorts, null, _dbClient, _coordinator,
                        poolsToMatchWithVpool);
            } catch (Exception ex) {
                _logger.error("Exception occurred during discovery.", ex);
                throw ex;
            }

        } else {
            _logger.error("Unidentified storage system {} found", array.getObjectID());
        }

    }

    /**
     * Parse & process the TieringPolicies of a given system.
     * 
     * @param array
     * @param accessProfile
     * @throws Exception
     */
    private void parseDiscoveryTieringPolicyResponse(StorageArray array, AccessProfile accessProfile) throws Exception {
        String nativeGuid = NativeGUIDGenerator.generateNativeGuid(
                DiscoveredDataObject.Type.hds.toString(), array.getObjectID());
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getStorageSystemByNativeGuidConstraint(nativeGuid), queryResult);
        if (queryResult.iterator().hasNext()) {
            URI systemURI = queryResult.iterator().next();
            if (null != systemURI) {
                try {
                    StorageSystem system = _dbClient.queryObject(StorageSystem.class,
                            systemURI);
                    if (null == system) {
                        return;
                    }
                    List<TieringPolicy> tpList = array.getTieringPolicyList();
                    if (null != tpList && !tpList.isEmpty()) {
                        processDiscoveredTieringPolicies(system, tpList);
                    } else {
                        _logger.info("No tieringPolicies defined for the system: {}", systemURI);
                    }
                } catch (Exception ex) {
                    _logger.error("Exception occurred during discovery of Tiering Policies.", ex);
                    throw ex;
                }
            }
        }
    }

    /**
     * Process the tieringpolicies received for a given system.
     * 
     * @param system
     * @param tpList
     */
    private void processDiscoveredTieringPolicies(StorageSystem system,
            List<TieringPolicy> tpList) {
        List<AutoTieringPolicy> newPolicies = new ArrayList<AutoTieringPolicy>();
        List<AutoTieringPolicy> allPolicies = new ArrayList<AutoTieringPolicy>();
        List<AutoTieringPolicy> updatePolicies = new ArrayList<AutoTieringPolicy>();
        for (TieringPolicy tpFromResponse : tpList) {
            // Ignore all custom tiering policies.
            if (Integer.parseInt(tpFromResponse.getPolicyID()) > 5) {
                _logger.debug("Ignoring custom policy {} on system {} ", tpFromResponse.getPolicyID(),
                        system.getNativeGuid());
                continue;
            }
            String nativeGuid = NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(system.getNativeGuid(),
                    getTieringPolicyLabel(tpFromResponse.getPolicyID()), NativeGUIDGenerator.AUTO_TIERING_POLICY);
            AutoTieringPolicy tieringPolicy = checkTieringPolicyExistsInDB(nativeGuid);
            boolean isNew = false;
            if (null == tieringPolicy) {
                isNew = true;
                tieringPolicy = new AutoTieringPolicy();
                tieringPolicy.setId(URIUtil.createId(AutoTieringPolicy.class));
                tieringPolicy.setPolicyName(getTieringPolicyLabel(tpFromResponse.getPolicyID()));
                tieringPolicy.setStorageSystem(system.getId());
                tieringPolicy.setNativeGuid(nativeGuid);
                tieringPolicy.setLabel(getTieringPolicyLabel(tpFromResponse.getPolicyID()));
                tieringPolicy.setSystemType(Type.hds.name());
                newPolicies.add(tieringPolicy);
            }
            // Hitachi is not providing any API to check whether a policy is enabled or not.
            // Hence enabling by default for all policies.
            tieringPolicy.setPolicyEnabled(Boolean.TRUE);
            tieringPolicy
                    .setProvisioningType(ProvisioningType.ThinlyProvisioned
                            .name());
            if (!isNew) {
                updatePolicies.add(tieringPolicy);
            }
        }
        _dbClient.createObject(newPolicies);
        _dbClient.persistObject(updatePolicies);
        allPolicies.addAll(newPolicies);
        allPolicies.addAll(updatePolicies);
    }

    /**
     * Returns the policy name like HiCommand Suite.
     * 
     * @param policyID
     * @return
     */
    private String getTieringPolicyLabel(String policyID) {
        return HitachiTieringPolicy.getType(policyID).replaceAll(HDSConstants.UNDERSCORE_OPERATOR,
                HDSConstants.SLASH_OPERATOR);
    }

    /**
     * Verify whether tieringPolicy already exists in DB or not.
     * 
     * @param nativeGuid
     * @return
     */
    private AutoTieringPolicy checkTieringPolicyExistsInDB(String nativeGuid) {
        AutoTieringPolicy tieringPolicy = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        // use NativeGuid to lookup Pools in DB
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                .getAutoTieringPolicyByNativeGuidConstraint(nativeGuid), queryResult);
        if (queryResult.iterator().hasNext()) {
            URI tieringPolicyURI = queryResult.iterator().next();
            if (null != tieringPolicyURI) {
                tieringPolicy = _dbClient.queryObject(AutoTieringPolicy.class,
                        tieringPolicyURI);
            }
        }
        return tieringPolicy;
    }

    /**
     * Process the system response and update storagesystem object.
     * 
     * @param system
     * @param array
     */
    private void processStorageSystemResponse(StorageSystem system,
            StorageArray array) {
        system.setFirmwareVersion(array.getControllerVersion());
        String arrayIpAddress = null;
        // Since Hicommand XML API is not providing a property to get the ipaddress.
        // We should parse name & description to find the ipaddress of the SVP.
        if (null != array.getName()
                && array.getName().contains(HDSConstants.AT_THE_RATE_SYMBOL)) {
            arrayIpAddress = HDSUtils.extractIpAddress(array.getName(), 2,
                    HDSConstants.AT_THE_RATE_SYMBOL);
        } else {
            int length = array.getDescription().split(HDSConstants.SPACE_STR).length - 1;
            arrayIpAddress = HDSUtils.extractIpAddress(array.getDescription(),
                    length, HDSConstants.SPACE_STR);
        }
        system.setIpAddress(arrayIpAddress);
        system.setLabel(array.getName());
        system.setReachableStatus(Boolean.TRUE);
        // TODO needs to set compatible status based on the firmware check
        system.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
        system.setAutoTieringEnabled(Boolean.TRUE);

        _dbClient.persistObject(system);
    }

    /**
     * 
     * @param system
     * @param portList
     * @param accessProfile
     * @throws IOException
     */
    private List<StoragePort> processStoragePortResponse(StorageSystem system, List<Port> portList,
            AccessProfile accessProfile, Set<String> supportedProtocols) throws IOException {
        List<StoragePort> newPorts = new ArrayList<StoragePort>();
        List<StoragePort> allPorts = new ArrayList<StoragePort>();
        List<StoragePort> updatePorts = new ArrayList<StoragePort>();
        if (null != portList && !portList.isEmpty()) {
            for (Port portFromResponse : portList) {
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                        portFromResponse.getObjectID(), NativeGUIDGenerator.PORT);
                StoragePort port = checkPortExistsInDB(nativeGuid);
                boolean isNew = false;
                if (null == port) {
                    isNew = true;
                    port = new StoragePort();
                    port.setId(URIUtil.createId(StoragePort.class));
                    port.setStorageDevice(system.getId());
                    port.setStorageHADomain(getStorageAdapterRef(portFromResponse,
                            system));
                    port.setNativeGuid(nativeGuid);
                    port.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                            .toString());
                    newPorts.add(port);
                }
                port.setPortSpeed(portFromResponse.getChannelSpeed());
                port.setPortNetworkId(portFromResponse.getWwpn());
                if (HDSConstants.TARGET.equalsIgnoreCase(portFromResponse.getPortRole())) {
                    port.setPortType(PortType.frontend.name());
                } else {
                    port.setPortType(PortType.Unknown.name());
                }
                // @TODO extract the operationalStatus from topology and set.
                // if (portFromResponse.getTopology().contains("LinkUp")) {
                port.setOperationalStatus(OperationalStatus.OK.toString());
                /*
                 * } else if(portFromResponse.getTopology().contains("Failure")) {
                 * port.setOperationalStatus(OperationalStatus.NOT_OK.toString());
                 * }
                 */

                if (HDSConstants.FIBRE.equalsIgnoreCase(portFromResponse.getPortType())) {
                    port.setTransportType(HDSConstants.FC);
                    addProtocolIfNotExists(supportedProtocols, HDSConstants.FC);
                } else if (HDSConstants.ISCSI.equalsIgnoreCase(portFromResponse.getPortType())) {
                    port.setTransportType(HDSConstants.IP);
                    addProtocolIfNotExists(supportedProtocols, HDSConstants.iSCSI);
                }
                port.setPortGroup(portFromResponse.getPortControllerID());
                port.setLabel(portFromResponse.getDisplayName());
                port.setPortName(portFromResponse.getDisplayName());
                port.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                port.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());
                if (!isNew) {
                    updatePorts.add(port);
                }

            }
            _dbClient.createObject(newPorts);
            _dbClient.persistObject(updatePorts);
            allPorts.addAll(newPorts);
            allPorts.addAll(updatePorts);
        }
        return allPorts;

    }

    /**
     * Verify whether protocolType already exists or not. If it doesn't exist
     * then add.
     * 
     * @param protocols
     * @param protocolType
     */
    private void addProtocolIfNotExists(Set<String> protocols, String protocolType) {
        if (!protocols.contains(protocolType)) {
            protocols.add(protocolType);
        }
    }

    /**
     * Return the StorageAdapter information of a port it belongs to.
     * 
     * @param port : port to find the adapter info.
     * @param system : storage system details.
     * @return : URI of the StorageHADomain.
     * @throws IOException
     */
    private URI getStorageAdapterRef(Port port, StorageSystem system) throws IOException {
        URIQueryResultList queryResult = new URIQueryResultList();
        URI uri = null;
        // CONTROLLER.D800S.83041093.0
        String adapterObjectID = generateObjectID(HDSConstants.CONTROLLER, port.getArrayType(), port.getSerialNumber(),
                port.getPortControllerID());
        String adapterNativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                adapterObjectID, NativeGUIDGenerator.ADAPTER);
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getStorageHADomainByNativeGuidConstraint(adapterNativeGuid), queryResult);
        if (queryResult.iterator().hasNext()) {
            uri = queryResult.iterator().next();
        }
        return uri;
    }

    /**
     * Utility to generate ObjectId.
     * 
     * @param componentKey
     * @param arrayType
     * @param serialNumber
     * @param nativeID
     * @return
     */
    private String generateObjectID(String componentKey, String arrayType,
            String serialNumber, String nativeID) {
        String[] uriParams = new String[] { componentKey, arrayType, serialNumber, nativeID };
        return MessageFormatter.arrayFormat(HDSConstants.HDS_OBJECT_ID_FORMAT, uriParams).getMessage();
    }

    /**
     * Process the StorageAdapter response received from the server.
     * 
     * @param system : storagesystem details.
     * @param portControllerList : List of portcontrollers info received from server.
     * @param accessProfile : accessProfile details.
     * @throws IOException
     */
    private void processStorageAdapterResponse(StorageSystem system,
            List<PortController> portControllerList, AccessProfile accessProfile)
            throws IOException {
        List<StorageHADomain> newAdapters = new ArrayList<StorageHADomain>();
        List<StorageHADomain> updateAdapters = new ArrayList<StorageHADomain>();
        if (null != portControllerList && !portControllerList.isEmpty()) {
            for (PortController portControllerFromResponse : portControllerList) {
                boolean isNew = false;
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                        portControllerFromResponse.getObjectID(),
                        NativeGUIDGenerator.ADAPTER);
                StorageHADomain adapter = checkAdapterExistsInDB(nativeGuid);
                if (null == adapter) {
                    isNew = true;
                    adapter = new StorageHADomain();
                    adapter.setStorageDeviceURI(system.getId());
                    adapter.setId(URIUtil.createId(StorageHADomain.class));
                    adapter.setAdapterName(portControllerFromResponse.getDisplayName());
                    adapter.setLabel(portControllerFromResponse.getDisplayName());
                    adapter.setNativeGuid(nativeGuid);
                    newAdapters.add(adapter);
                }
                adapter.setAdapterName(portControllerFromResponse.getDisplayName());
                adapter.setLabel(portControllerFromResponse.getDisplayName());
                if (!HDSConstants.NO_CLUSTER_ID.equalsIgnoreCase(portControllerFromResponse.getCluster())) {
                    adapter.setSlotNumber(portControllerFromResponse.getCluster());
                }
                adapter.setName(portControllerFromResponse.getDisplayName());
                if (!isNew) {
                    updateAdapters.add(adapter);
                }

            }
            _dbClient.createObject(newAdapters);
            _dbClient.persistObject(updateAdapters);
        }
    }

    /**
     * Check if Adapter exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
     * @return
     * @throws IOException
     */
    protected StorageHADomain checkAdapterExistsInDB(String nativeGuid) throws IOException {
        StorageHADomain adapter = null;
        // use NativeGuid to lookup Pools in DB
        List<StorageHADomain> adapterInDB = CustomQueryUtility.getActiveStorageHADomainByNativeGuid(_dbClient, nativeGuid);
        if (adapterInDB != null && !adapterInDB.isEmpty()) {
            adapter = adapterInDB.get(0);
        }
        return adapter;
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
    protected StoragePool checkPoolExistsInDB(String nativeGuid) throws IOException {
        StoragePool pool = null;
        // use NativeGuid to lookup Pools in DB
        List<StoragePool> poolInDB = CustomQueryUtility.getActiveStoragePoolByNativeGuid(_dbClient, nativeGuid);
        if (poolInDB != null && !poolInDB.isEmpty()) {
            pool = poolInDB.get(0);
        }

        return pool;
    }

    /**
     * Check if Port exists in DB.
     * 
     * @param poolInstance
     * @param _dbClient
     * @param profile
     * @return
     * @throws IOException
     */
    protected StoragePort checkPortExistsInDB(String nativeGuid) throws IOException {
        StoragePort port = null;
        // use NativeGuid to lookup Pools in DB
        List<StoragePort> portInDB = CustomQueryUtility.getActiveStoragePortByNativeGuid(_dbClient, nativeGuid);
        if (portInDB != null && !portInDB.isEmpty()) {
            port = portInDB.get(0);
        }
        return port;
    }

    /**
     * Process the ThickPool response received from server.
     * 
     * @param system
     * @param thickPoolListFromResponse
     * @param accessProfile
     * @param supportedProtocols
     * @param poolsToMatchWithVpool
     * @throws IOException
     */
    private List<StoragePool> processStorageThickPoolResponse(StorageSystem system,
            List<Pool> thickPoolListFromResponse, AccessProfile accessProfile,
            Set<String> supportedProtocols, List<StoragePool> poolsToMatchWithVpool) throws IOException {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> updatePools = new ArrayList<StoragePool>();
        List<StoragePool> allPools = new ArrayList<StoragePool>();

        if (null != thickPoolListFromResponse && !thickPoolListFromResponse.isEmpty()) {
            _logger.debug("thickPoolListFromResponse.size() :{}", thickPoolListFromResponse.size());
            for (Pool poolFromResponse : thickPoolListFromResponse) {
                _logger.debug("Pool Id:{}", poolFromResponse.getPoolID());
                // Thick pool's type should be either 0/1 or -1. we should ignore thin pools here.
                if (!(0 == Integer.valueOf(poolFromResponse.getType()) || -1 == Integer.valueOf(poolFromResponse.getType())
                || 1 == Integer.valueOf(poolFromResponse.getType()))) {
                    continue;
                }
                boolean isNew = false;
                boolean isModified = false;   // indicates whether to add to modified pools list or not
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                        poolFromResponse.getObjectID(), NativeGUIDGenerator.POOL);
                _logger.debug("nativeGuid :{}", nativeGuid);
                StoragePool pool = checkPoolExistsInDB(nativeGuid);
                if (null == pool) {
                    isNew = true;
                    pool = new StoragePool();
                    pool.setNativeGuid(nativeGuid);
                    pool.setStorageDevice(system.getId());
                    pool.setId(URIUtil.createId(StoragePool.class));
                    pool.setNativeId(poolFromResponse.getPoolID());
                    pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY
                            .toString());
                    // @TODO hard coded for now. Need to see how to get it from API or documentation.
                    pool.setMaximumThickVolumeSize(104857600L);
                    pool.setPoolServiceType(PoolServiceType.block.toString());
                    pool.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                            .toString());

                    StringSet raidLevels = new StringSet();
                    // raid level for thick pools will be like RAID5(7D+1P), RAID1(1D+1D)
                    // we needs to truncate (7D+1P)
                    String raidLevel = parseRaidLevel(poolFromResponse.getRaidType());
                    if (StringUtils.isNotEmpty(raidLevel)) {
                        raidLevels.add(raidLevel);
                    }
                    pool.addSupportedRaidLevels(raidLevels);
                    pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THICK_ONLY
                            .toString());
                    _logger.info("poolType {} {}", poolFromResponse.getType(), poolFromResponse.getObjectID());
                    /*
                     * // Only Basic volumes are allowed to create.
                     * if (0 == Integer.valueOf(poolFromResponse.getType()) || -1 == Integer.valueOf(poolFromResponse.getType())) {
                     * 
                     * } else if (Integer.valueOf(poolFromResponse.getType()) == 4 || Integer.valueOf(poolFromResponse.getType()) == 3) {
                     * // Only DP volumes are allowed to create.
                     * pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_ONLY
                     * .toString());
                     * }
                     */
                }
                StringSet protocols = new StringSet(supportedProtocols);
                if (!isNew && ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getProtocols(), protocols)) {
                    isModified = true;
                }
                pool.setProtocols(protocols);
                pool.setPoolName(poolFromResponse.getDisplayName());
                pool.setFreeCapacity(poolFromResponse.getFreeCapacity());

                // UNSYNC_ASSOC -> snapshot, UNSYNC_UNASSOC -> clone
                StringSet copyTypes = new StringSet();
                copyTypes.add(StoragePool.CopyTypes.UNSYNC_ASSOC.name());
                copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
                copyTypes.add(StoragePool.CopyTypes.SYNC.name());
                copyTypes.add(StoragePool.CopyTypes.ASYNC.name());

                pool.setSupportedCopyTypes(copyTypes);

                // There is not direct way to get total capacity. totaCapacity = allocated capacity of allocated volumes + unallocated
                // volumes
                pool.setTotalCapacity(poolFromResponse.getFreeCapacity() + poolFromResponse.getUsedCapacity());
                if (StringUtils.isNotBlank(poolFromResponse.getDisplayName())) {
                    pool.setLabel(poolFromResponse.getDisplayName().length() == 1 ? " "
                            + poolFromResponse.getDisplayName() : poolFromResponse
                            .getDisplayName());
                }

                if (!isNew && !isModified &&
                        (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                                CompatibilityStatus.COMPATIBLE.name()) ||
                        ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(),
                                DiscoveryStatus.VISIBLE.name()))) {
                    isModified = true;
                }
                pool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());

                if (isNew) {
                    newPools.add(pool);
                    // add new pools to modified pools list to consider them for implicit pool matching.
                    poolsToMatchWithVpool.add(pool);
                } else {
                    updatePools.add(pool);
                    // add to modified pools list if pool's property which is required for vPool matcher, has changed.
                    if (isModified) {
                        poolsToMatchWithVpool.add(pool);
                    }
                }

            }

            StoragePoolAssociationHelper.setStoragePoolVarrays(system.getId(), newPools, _dbClient);

            _logger.debug("newPools size:{}", newPools.size());
            _logger.debug("updatePools size:{}", updatePools.size());
            _dbClient.createObject(newPools);
            _dbClient.persistObject(updatePools);
            allPools.addAll(newPools);
            allPools.addAll(updatePools);
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return allPools;
    }

    /**
     * Raid level for thick pools will be RAID5(7D+1P), RAID1(1D+1D) or "-"
     * 
     * @param raidType
     * @return truncated raid level
     */
    private String parseRaidLevel(String raidLevel) {
        String result = null;
        _logger.debug("Raid Level recived from hds :{}", raidLevel);
        if (raidLevel != null && raidLevel.length() > 4 &&
                raidLevel.contains("(")) {
            result = raidLevel.substring(0, raidLevel.indexOf("("));
            // RAID1+0(2D+2D) -> RAID10
            if (result.contains("+")) {
                result = new StringBuilder(result).deleteCharAt(result.indexOf("+")).toString();
            }
        }
        _logger.debug("Raid Level after parsing :{}", result);
        return result;
    }

    /**
     * Process the thin pool response received from server.
     * 
     * @param system
     * @param thinPoolListFromResponse
     * @param accessProfile
     * @param supportedProtocols
     * @param poolsToMatchWithVpool
     * @throws IOException
     */
    private List<StoragePool> processStorageThinPoolResponse(StorageSystem system,
            List<Pool> thinPoolListFromResponse, AccessProfile accessProfile,
            Set<String> supportedProtocols, List<StoragePool> poolsToMatchWithVpool) throws IOException {
        _logger.debug("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        List<StoragePool> newPools = new ArrayList<StoragePool>();
        List<StoragePool> updatePools = new ArrayList<StoragePool>();
        List<StoragePool> allPools = new ArrayList<StoragePool>();

        if (null != thinPoolListFromResponse && !thinPoolListFromResponse.isEmpty()) {
            _logger.debug("thinPoolListFromResponse size:{}", thinPoolListFromResponse.size());
            for (Pool poolFromResponse : thinPoolListFromResponse) {

                if (poolFromResponse.getPoolFunction() == 5) {
                    boolean isNew = false;
                    boolean isModified = false;   // indicates whether to add to modified pools list or not
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(system,
                            poolFromResponse.getObjectID(), NativeGUIDGenerator.POOL);
                    _logger.debug("nativeGuid :{}", nativeGuid);
                    StoragePool pool = checkPoolExistsInDB(nativeGuid);
                    if (null == pool) {
                        isNew = true;
                        pool = new StoragePool();
                        pool.setNativeGuid(nativeGuid);
                        pool.setStorageDevice(system.getId());
                        pool.setId(URIUtil.createId(StoragePool.class));
                        pool.setNativeId(poolFromResponse.getPoolID());
                        pool.setOperationalStatus(StoragePool.PoolOperationalStatus.READY
                                .toString());
                        // @TODO hard coded for now. Need to see how to get it from API or documentation.
                        pool.setMaximumThinVolumeSize(104857600L);
                        pool.setPoolServiceType(PoolServiceType.block.toString());
                        pool.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED
                                .toString());

                        StringSet raidLevels = new StringSet();
                        raidLevels.add(poolFromResponse.getRaidType());
                        //
                        pool.addSupportedRaidLevels(raidLevels);
                        pool.setSupportedResourceTypes(StoragePool.SupportedResourceTypes.THIN_ONLY
                                .toString());
                        _logger.info("pool objectId {}", poolFromResponse.getObjectID());

                        pool.setThinVolumePreAllocationSupported(Boolean.FALSE);
                    }

                    // Set TieringEnabled flag based on tierControl of the pool.
                    if (poolFromResponse.getTierControl() == 1) {
                        pool.setAutoTieringEnabled(Boolean.TRUE);
                    } else {
                        pool.setAutoTieringEnabled(Boolean.FALSE);
                    }

                    StringSet protocols = new StringSet(supportedProtocols);
                    if (!isNew && ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getProtocols(), protocols)) {
                        isModified = true;
                    }
                    pool.setProtocols(protocols);

                    StringSet copyTypes = new StringSet();
                    copyTypes.add(StoragePool.CopyTypes.UNSYNC_ASSOC.name());
                    copyTypes.add(StoragePool.CopyTypes.UNSYNC_UNASSOC.name());
                    copyTypes.add(StoragePool.CopyTypes.SYNC.name());
                    copyTypes.add(StoragePool.CopyTypes.ASYNC.name());
                    pool.setSupportedCopyTypes(copyTypes);

                    String label = null;
                    if (StringUtils.isBlank(poolFromResponse.getName()) || poolFromResponse.getName().length() <= 2) {
                        label = "DP " + poolFromResponse.getDisplayName();
                    } else {
                        label = poolFromResponse.getName();
                    }
                    pool.setPoolName(label);
                    pool.setFreeCapacity(poolFromResponse.getFreeCapacity());
                    pool.setTotalCapacity(poolFromResponse.getUsedCapacity());
                    pool.setSubscribedCapacity(poolFromResponse.getSubscribedCapacityInKB());

                    if (null != poolFromResponse.getDiskType()) {
                        Set<String> driveTypes = new HashSet<String>();
                        driveTypes.add(getPoolSupportedDriveType(poolFromResponse.getDiskType()));
                        pool.addDriveTypes(driveTypes);
                    }

                    // TODO workaround to display the display name based on the pool name
                    pool.setLabel(label);

                    if (!isNew && !isModified &&
                            (ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getCompatibilityStatus(),
                                    CompatibilityStatus.COMPATIBLE.name()) ||
                            ImplicitPoolMatcher.checkPoolPropertiesChanged(pool.getDiscoveryStatus(),
                                    DiscoveryStatus.VISIBLE.name()))) {
                        isModified = true;
                    }
                    pool.setCompatibilityStatus(CompatibilityStatus.COMPATIBLE.name());
                    pool.setDiscoveryStatus(DiscoveryStatus.VISIBLE.name());

                    if (isNew) {
                        newPools.add(pool);
                        // add new pools to modified pools list to consider them for implicit pool matching.
                        poolsToMatchWithVpool.add(pool);
                    } else {
                        updatePools.add(pool);
                        // add to modified pools list if pool's property which is required for vPool matcher, has changed.
                        if (isModified) {
                            poolsToMatchWithVpool.add(pool);
                        }
                    }
                }
            }
            StoragePoolAssociationHelper.setStoragePoolVarrays(system.getId(), newPools, _dbClient);

            _logger.info("New pools size: {}", newPools.size());
            _logger.info("updatePools size: {}", updatePools.size());
            _dbClient.createObject(newPools);
            _dbClient.persistObject(updatePools);
            allPools.addAll(newPools);
            allPools.addAll(updatePools);
        }
        _logger.debug("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        return allPools;
    }

    /**
     * Return the supportedDrive for a Pool.
     * 
     * @param diskType
     * @return
     */
    private String getPoolSupportedDriveType(String diskType) {
        String supportedDriveType = SupportedDriveTypeValues.UNKNOWN.name();
        if (HDSConstants.SATA_DRIVE_VALUE.equalsIgnoreCase(diskType)) {
            supportedDriveType = SupportedDriveTypeValues.SATA.name();
        } else if (HDSConstants.SAS_DRIVE_VALUE.equalsIgnoreCase(diskType)) {
            supportedDriveType = SupportedDriveTypeValues.SAS.name();
        } else if (HDSConstants.SSD_DRIVE_VALUE.equalsIgnoreCase(diskType)) {
            supportedDriveType = SupportedDriveTypeValues.SSD.name();
        }
        return supportedDriveType;
    }

    /**
     * Creates a new WEBClient for a given IP, based on AccessProfile
     * 
     * @param accessProfile
     *            : AccessProfile for the providers
     * @throws WBEMException
     *             : if WBEMException while creating the WBEMClient
     * @throws SMIPluginException
     * @return WBEMClient : initialized instance of WBEMClientCIMXML
     */
    private WBEMClient getCIMClient(AccessProfile accessProfile) throws Exception {
        String protocol = Boolean.valueOf(accessProfile.getSslEnable()) ? CimConstants.SECURE_PROTOCOL
                : CimConstants.DEFAULT_PROTOCOL;
        CIMObjectPath path = CimObjectPathCreator.createInstance(protocol,
                accessProfile.getIpAddress(),
                Integer.toString(accessProfile.getPortNumber()),
                accessProfile.getInteropNamespace(), null, null);
        try {
            Subject subject = new Subject();
            subject.getPrincipals().add(new UserPrincipal(accessProfile.getUserName()));
            subject.getPrivateCredentials().add(
                    new PasswordCredential(accessProfile.getPassword()));
            wbemClient = WBEMClientFactory
                    .getClient(CimConstants.CIM_CLIENT_PROTOCOL);

            // Operations block by default, so a timeout must be set in case the
            // CIM server becomes unreachable.
            // Commenting out, as timeout had been moved to cimom.properties
            // file
            // _cimClient.setProperty(WBEMClientConstants.PROP_TIMEOUT,
            // CimConstants.CIM_CLIENT_TIMEOUT);
            wbemClient.initialize(path, subject, null);

        } catch (Exception e) {
            _logger.error("Could not establish connection for {}", accessProfile.getIpAddress(), e);
            wbemClient.close();
            throw e;
        }
        return wbemClient;
    }

    /**
     * Initialize the Map
     * 
     * @param _keyMap
     * @param cacheVolumes
     * @param cachePools
     * @param accessProfile
     */
    private void initMap(AccessProfile accessProfile) {
        _keyMap.put(Constants._computerSystem, CimObjectPathCreator.createInstance(Constants._cimSystem,
                accessProfile.getInteropNamespace()));
        _keyMap.put(Constants._cimClient, wbemClient);
        _keyMap.put(Constants._serialID, accessProfile.getserialID());
        _keyMap.put(Constants.dbClient, _dbClient);
        _keyMap.put(Constants._TimeCollected, System.currentTimeMillis());
        _keyMap.put(Constants._Stats, new LinkedList<Stat>());
        _keyMap.put(Constants._InteropNamespace, accessProfile.getInteropNamespace());
        _keyMap.put(Constants.ACCESSPROFILE, accessProfile);
        _keyMap.put(Constants.PROPS, accessProfile.getProps());
        _keyMap.put(Constants.STORAGEPROCESSORS, new LinkedList<CIMObjectPath>());
        _keyMap.put(Constants.STORAGEPORTS, new LinkedList<CIMObjectPath>());
    }

    /**
     * releaseResources
     */
    private void releaseResources() {
        wbemClient.close();
        _keyMap.clear();
        namespaces = null;
    }

    public void setPortMetricsProcessor(PortMetricsProcessor portMetricsProcessor) {
        this.portMetricsProcessor = portMetricsProcessor;
    }

}
