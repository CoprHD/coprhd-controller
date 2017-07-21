/*
 * Copyright (c) 2016 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.volumecontroller.impl.plugins;


import static com.emc.storageos.db.client.util.CustomQueryUtility.queryActiveResourcesByAltId;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.constraint.ContainmentConstraint;
import com.emc.storageos.db.client.constraint.URIQueryResultList;
import com.emc.storageos.db.client.model.AutoTieringPolicy;
import com.emc.storageos.db.client.model.DataObject;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StorageHADomain;
import com.emc.storageos.db.client.model.StorageSystemType;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.plugins.StorageSystemViewObject;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.StorageDriver;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.storagedriver.storagecapabilities.AutoTieringPolicyCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.CapabilityInstance;
import com.emc.storageos.storagedriver.storagecapabilities.DeduplicationCapabilityDefinition;
import com.emc.storageos.storagedriver.storagecapabilities.VolumeCompressionCapabilityDefinition;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceCollectionException;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceUnManagedVolumeDiscoverer;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

/**
 * ExtendedCommunicationInterface implementation for SB SDK managed devices.
 */

public class ExternalDeviceCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {

    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private Logger _log = LoggerFactory.getLogger(ExternalDeviceCommunicationInterface.class);
    private Map<String, AbstractStorageDriver> drivers;
    // Indicate if driver info has been fetched from db and merged into drivers member
    private boolean initialized = false;
    
    // The common capability definitions supported by the SB SDK.
    private Map<String, CapabilityDefinition> capabilityDefinitions;

    private ExternalDeviceUnManagedVolumeDiscoverer unManagedVolumeDiscoverer;
    private ExternalDeviceUnManagedVolumeDiscoverer unManagedFileSystemDiscoverer;

    // Initialized drivers map
    private Map<String, AbstractStorageDriver> discoveryDrivers = new HashMap<>();

    public void setUnManagedVolumeDiscoverer(ExternalDeviceUnManagedVolumeDiscoverer unManagedVolumeDiscoverer) {
        this.unManagedVolumeDiscoverer = unManagedVolumeDiscoverer;
    }

    public void setDrivers(Map<String, AbstractStorageDriver> drivers) {
        this.drivers = drivers;
    }
    
    /**
     * Setter for the common capability definitions supported by the SB SDK.
     * 
     * @param capabilityDefinitions The map common of capability definitions keyed by their unique id.
     */
    public void setCapabilityDefinitions(Map<String, CapabilityDefinition> capabilityDefinitions) {
        this.capabilityDefinitions = capabilityDefinitions;
    }    

    private void initDrivers() {
        if (initialized) {
            return;
        }
        List<URI> ids = _dbClient.queryByType(StorageSystemType.class, true);
        Iterator<StorageSystemType> it = _dbClient.queryIterativeObjects(StorageSystemType.class, ids);
        Map<String, AbstractStorageDriver> cachedDriverInstances = new HashMap<>();
        while (it.hasNext()) {
            StorageSystemType type = it.next();
            if (type.getIsNative() == null ||type.getIsNative()) {
                continue;
            }
            if (!StringUtils.equals(type.getMetaType(), StorageSystemType.META_TYPE.BLOCK.toString())) {
                continue;
            }
            String typeName = type.getStorageTypeName();
            String className = type.getDriverClassName();
            // provider and managed system should use the same driver instance
            if (cachedDriverInstances.containsKey(className)) {
                drivers.put(typeName, cachedDriverInstances.get(className));
                _log.info("Driver info for storage system type {} has been set into externaldevice instance", typeName);
                continue;
            }
            String mainClassName = type.getDriverClassName();
            try {
                AbstractStorageDriver driverInstance = (AbstractStorageDriver) Class.forName(mainClassName) .newInstance();
                drivers.put(typeName, driverInstance);
                cachedDriverInstances.put(className, driverInstance);
                _log.info("Driver info for storage system type {} has been set into externaldevice instance", typeName);
            } catch (Exception e) {
                _log.error("Error happened when instantiating class {}", mainClassName);
            }
        }
        initialized = true;
    }

    /**
     * Get device driver based on the driver type.
     * @param driverType
     * @return driver
     */
    private synchronized AbstractStorageDriver getDriver(String driverType) {
        // look up driver
        AbstractStorageDriver discoveryDriver = discoveryDrivers.get(driverType);
        if (discoveryDriver != null) {
            return discoveryDriver;
        } else {
            // init driver
            AbstractStorageDriver driver = drivers.get(driverType);
            if (driver == null) {
                initDrivers();
                driver = drivers.get(driverType);
                if (driver == null) {
                    _log.info("No driver entry defined for device type: {} . ", driverType);
                    return null;
                }
            }
            init(driver);
            discoveryDrivers.put(driverType, driver);
            return driver;
        }
    }


    /**
     * Init device driver. Sets registry and lock manager to the driver.
     * @param driver
     */
    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance(_dbClient);
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(_locker);
        driver.setLockManager(lockManager);
        driver.setSdkVersionNumber(StorageDriver.SDK_VERSION_NUMBER);
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException {
        // todo
        _log.info("Entering {}", Thread.currentThread().getStackTrace()[1].getMethodName());
        _log.info("Collect statistic information for external device of type {} is not supported", accessProfile.getSystemType());
        _log.info("Exiting {}", Thread.currentThread().getStackTrace()[1].getMethodName());
    }

    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {
        // Initialize driver instance for storage provider,
        // call driver to scan the provider to get list of managed storage systems,
        // update the system with this information.
        _log.info("Scanning started for provider: {}", accessProfile.getSystemId());
        com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus cxnStatus =
                com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus.CONNECTED;
        // Get discovery driver class based on storage device type
        String deviceType = accessProfile.getSystemType();
        AbstractStorageDriver driver = getDriver(deviceType);
        if (driver == null) {
            String errorMsg = String.format("No driver entry defined for device type: %s . ", deviceType);
            _log.info(errorMsg);
            throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                    null, errorMsg, null, null);
        }

        com.emc.storageos.db.client.model.StorageProvider storageProvider = null;
        try {
            storageProvider =
                    _dbClient.queryObject(com.emc.storageos.db.client.model.StorageProvider.class, accessProfile.getSystemId());
            String username = storageProvider.getUserName();
            String password = storageProvider.getPassword();
            String hostName = storageProvider.getIPAddress();
            Integer portNumber = storageProvider.getPortNumber();
            String providerType = storageProvider.getInterfaceType();
            Boolean useSsl = storageProvider.getUseSSL();
            String msg = String.format("Storage provider info: type: %s, host: %s, port: %s, user: %s, useSsl: %s",
                    providerType, hostName, portNumber, username, useSsl);
            _log.info(msg);

            StorageProvider driverProvider = new StorageProvider();
            // initialize driver provider
            driverProvider.setProviderHost(hostName);
            driverProvider.setPortNumber(portNumber);
            driverProvider.setUsername(username);
            driverProvider.setPassword(password);
            driverProvider.setUseSSL(useSsl);

            // call the driver
            List<StorageSystem> systems = new ArrayList<>();
            DriverTask task = driver.discoverStorageProvider(driverProvider, systems);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // process results, populate cache
                _log.info("Scan: found {} systems for provider {}", systems.size(), accessProfile.getSystemId());

                //update provider with scan info
                storageProvider.setVersionString(driverProvider.getProviderVersion());
                if (driverProvider.isSupportedVersion()) {
                    storageProvider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                } else {
                    storageProvider.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                    String errorMsg = String.format("Storage provider %s has version %s which is not supported by driver",
                            storageProvider.getIPAddress(), storageProvider.getVersionString());
                    throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                            null, errorMsg, null, null);
                }

                // process storage system cache
                Map<String, StorageSystemViewObject> storageSystemsCache = accessProfile.getCache();
                for (StorageSystem driverStorageSystem : systems) {
                    String systemType = driverStorageSystem.getSystemType();
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(systemType, driverStorageSystem.getNativeId());
                    StorageSystemViewObject storageSystemView = storageSystemsCache.get(nativeGuid);
                    if (storageSystemView == null) {
                        storageSystemView = new StorageSystemViewObject();
                    }
                    storageSystemView.setDeviceType(systemType);
                    storageSystemView.addprovider(accessProfile.getSystemId().toString());
                    storageSystemView.setProperty(StorageSystemViewObject.SERIAL_NUMBER, driverStorageSystem.getSerialNumber());
                    storageSystemView.setProperty(StorageSystemViewObject.VERSION, driverStorageSystem.getFirmwareVersion());
                    storageSystemView.setProperty(StorageSystemViewObject.STORAGE_NAME, driverStorageSystem.getNativeId());
                    storageSystemsCache.put(nativeGuid, storageSystemView);
                    _log.info(String.format("Info for storage system %s (provider ip %s): type: %s, nativeGuid: %s",
                            driverStorageSystem.getSerialNumber(), accessProfile.getIpAddress(), systemType, nativeGuid));
                }
            } else {
                // task status is not ready
                String errorMsg = String.format("Failed to scan provider %s of type %s. \n" +
                                " Driver task message: %s", accessProfile.getSystemId(), accessProfile.getSystemType(),
                                task.getMessage());
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
        } catch (Exception ex) {
            _log.error("Error scanning provider: {} of type: {} .", accessProfile.getIpAddress(), accessProfile.getSystemType(), ex);
            cxnStatus = com.emc.storageos.db.client.model.StorageProvider.ConnectionStatus.NOTCONNECTED;
           throw ex;
        } finally {
            if (storageProvider != null) {
                storageProvider.setConnectionStatus(cxnStatus.name());
                _dbClient.updateObject(storageProvider);
            }
            _log.info("Completed scan of {} provider: ", accessProfile.getSystemType(), accessProfile.getIpAddress());
        }
    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {
        // Get discovery driver class based on storage device type
        String deviceType = accessProfile.getSystemType();
        AbstractStorageDriver driver = getDriver(deviceType);
        if (driver == null) {
            String errorMsg = String.format("No driver entry defined for device type: %s . ", deviceType);
            _log.info(errorMsg);
            throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                    null, errorMsg, null, null);
        }

        try {
            if (null != accessProfile.getnamespace()
                    && (accessProfile.getnamespace().equals(com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString()))) {
                discoverUnManagedBlockObjects(driver, accessProfile);
                _completer.statusReady(_dbClient, "Completed unmanaged block object discovery");
            } else if (null != accessProfile.getnamespace()
                    && (accessProfile.getnamespace().equals(com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces.UNMANAGED_FILESYSTEMS.toString()))){
               _log.warn("Discovery of unmanaged file systems is not supported for external storage system of type {}", accessProfile.getSystemType());
            } else {
                // discover storage system
                discoverStorageSystem(driver, accessProfile);
                _completer.statusPending(_dbClient, "Completed storage system discovery");

                // discover storage pools
                List<com.emc.storageos.db.client.model.StoragePool> storagePools = discoverStoragePools(driver, accessProfile);
                List<com.emc.storageos.db.client.model.StoragePool> storagePoolsToMatchWithVpools = new ArrayList<>();
                storagePoolsToMatchWithVpools.addAll(storagePools);
                List<com.emc.storageos.db.client.model.StoragePool> notVisiblePools = DiscoveryUtils.checkStoragePoolsNotVisible(storagePools,
                        _dbClient, accessProfile.getSystemId());
                storagePoolsToMatchWithVpools.addAll(notVisiblePools);
                _completer.statusPending(_dbClient, "Completed storage pools discovery");

                // discover ports
                List<com.emc.storageos.db.client.model.StoragePort> allPorts = new ArrayList<>();
                Set<Network> networksToUpdate = new HashSet<>();
                Map<String, List<com.emc.storageos.db.client.model.StoragePort>> ports = discoverStoragePorts(driver, networksToUpdate, accessProfile);
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

                if (!networksToUpdate.isEmpty()) {
                    _dbClient.updateObject(networksToUpdate);
                }

                List<com.emc.storageos.db.client.model.StoragePort> notVisiblePorts = DiscoveryUtils.checkStoragePortsNotVisible(allPorts,
                        _dbClient, accessProfile.getSystemId());
                List<com.emc.storageos.db.client.model.StoragePort> allExistPorts = new ArrayList<>(ports.get(EXISTING));
                allExistPorts.addAll(notVisiblePorts);
                _completer.statusPending(_dbClient, "Completed port discovery");

                StoragePortAssociationHelper.runUpdatePortAssociationsProcess(ports.get(NEW),
                        allExistPorts, _dbClient, _coordinator, storagePoolsToMatchWithVpools);

                _completer.statusReady(_dbClient, "Completed storage discovery");
            }
        } catch (BaseCollectionException bEx) {
            _completer.error(_dbClient, bEx);
            throw bEx;
        }
    }

    public void discoverUnManagedBlockObjects(AbstractStorageDriver driver, AccessProfile accessProfile) {
        String detailedStatusMessage;
        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());
        if (null == storageSystem) {
            return;
        }
        try {
            _log.info("discoverUnManagedBlockObjects information for storage system {}, native id {} - start",
                    accessProfile.getSystemId(), storageSystem.getNativeGuid());
            storageSystem.setDiscoveryStatus(DiscoveredDataObject.DataCollectionJobStatus.IN_PROGRESS.toString());
            _dbClient.updateObject(storageSystem);
            if (accessProfile.getnamespace().equals(com.emc.storageos.db.client.model.StorageSystem.Discovery_Namespaces.UNMANAGED_VOLUMES.toString())) {
                unManagedVolumeDiscoverer.discoverUnManagedBlockObjects((BlockStorageDriver) driver, storageSystem, _dbClient, _partitionManager);
            }

            // discovery succeeds
            detailedStatusMessage = String.format("discoverUnManagedBlockObjects completed successfully for %s: %s",
                    storageSystem.getNativeId(), storageSystem.getId().toString());
            _log.info(detailedStatusMessage);

        } catch (Exception e) {
            String message = String.format("discoverUnManagedBlockObjects failed for system %s with native id %s : %s .",
                    storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            storageSystem.setLastDiscoveryStatusMessage(message);
            throw e;
        } finally {
            _dbClient.updateObject(storageSystem);
            _log.info("discoverUnManagedBlockObjects for system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }
    }

    private void discoverStorageSystem(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {

        // TODO: how to set Site?
        StorageSystem driverStorageSystem = new StorageSystem();
        driverStorageSystem.setIpAddress(accessProfile.getIpAddress());
        driverStorageSystem.setPortNumber(accessProfile.getPortNumber());
        driverStorageSystem.setUsername(accessProfile.getUserName());
        driverStorageSystem.setPassword(accessProfile.getPassword());

        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());

        driverStorageSystem.setSystemName(storageSystem.getLabel());
        driverStorageSystem.setDisplayName(storageSystem.getLabel());

        // could be already populated by scan
        if (storageSystem.getSerialNumber() != null) {
            driverStorageSystem.setSerialNumber(storageSystem.getSerialNumber());
            _log.info("discoverStorageSystem: set serial number to {}", driverStorageSystem.getSerialNumber());
        }
        // could be already populated by scan
        if (storageSystem.getNativeId() != null) {
            driverStorageSystem.setNativeId(storageSystem.getNativeId());
            _log.info("discoverStorageSystem: set nativeId to {}", driverStorageSystem.getNativeId());
        }

        try {
            _log.info("discoverStorageSystem information for storage system {}, name {}, ip address (), port {} - start",
                    accessProfile.getSystemId(), driverStorageSystem.getSystemName(), driverStorageSystem.getIpAddress(),
                    driverStorageSystem.getPortNumber());
            DriverTask task = driver.discoverStorageSystem(driverStorageSystem);

            // process discovery results.
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY)  {
                // discovery completed
                storageSystem.setIsDriverManaged(true);
                storageSystem.setSerialNumber(driverStorageSystem.getSerialNumber());
                storageSystem.setNativeId(driverStorageSystem.getNativeId());
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(accessProfile.getSystemType(),
                        driverStorageSystem.getNativeId());
                storageSystem.setNativeGuid(nativeGuid);
                storageSystem.setFirmwareVersion(driverStorageSystem.getFirmwareVersion());

                if (driverStorageSystem.getSupportedReplications() != null) {
                    _log.info("Set async actions...");
                    StringSet asyncActions = new StringSet();
                    Set<StorageSystem.SupportedReplication> replications = driverStorageSystem.getSupportedReplications();
                    for (StorageSystem.SupportedReplication replication : replications) {
                        if (replication == StorageSystem.SupportedReplication.elementReplica) {
                            asyncActions.add(com.emc.storageos.db.client.model.StorageSystem.AsyncActions.CreateElementReplica.name());
                        } else if (replication == StorageSystem.SupportedReplication.groupReplica){
                            asyncActions.add(com.emc.storageos.db.client.model.StorageSystem.AsyncActions.CreateGroupReplica.name());
                        }
                    }
                    storageSystem.setSupportedAsynchronousActions(asyncActions);
                }

                if (driverStorageSystem.isSupportedVersion()) {
                    storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                    storageSystem.setReachableStatus(true);
                } else {
                    storageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                    storageSystem.setReachableStatus(false);
                    DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, storageSystem.getId());
                    String errorMsg = String.format("Storage array %s has firmware version %s which is not supported by driver",
                            storageSystem.getNativeGuid(), storageSystem.getFirmwareVersion());
                    throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                            null, errorMsg, null, null);
                }
            } else {
                storageSystem.setReachableStatus(false);
                String errorMsg = String.format("Failed to discover storage system %s of type %s. \n" +
                                " Driver task message: %s ",
                       accessProfile.getSystemId(), accessProfile.getSystemType(), task.getMessage());
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
            String message = String.format("Storage array %s with native id %s was discovered successfully.",
                    storageSystem.getId(), storageSystem.getNativeGuid());
            _log.info(message);
            storageSystem.setLastDiscoveryStatusMessage(message);
        } catch (Exception e) {
            if (storageSystem != null) {
                String message = String.format("Failed to discover storage array %s with native id %s : %s .",
                        storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
                storageSystem.setLastDiscoveryStatusMessage(message);
                _log.error(message, e);
            }
            throw e;
        } finally {
            if (storageSystem != null) {
                _dbClient.updateObject(storageSystem);
            }
            _log.info("Discovery of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }
    }

    private List<com.emc.storageos.db.client.model.StoragePool>  discoverStoragePools(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {
        
        // Discover storage pools and associated auto tiering policies.
        List<StoragePool> driverStoragePools = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePool> allPools = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePool> newPools = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePool> existingPools = new ArrayList<>();
        Map<String, List<com.emc.storageos.db.client.model.StoragePool>> autoTieringPolicyPoolMap = new HashMap<>();
        Map<String, Map<String, List<String>>> autoTieringPolicyPropertiesMap = new HashMap<>();

        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());
        URI storageSystemId = storageSystem.getId();

        StorageSystem driverStorageSystem = initStorageSystem(storageSystem);
        try {
            _log.info("discoverPools for storage system {} - start", storageSystemId);

            DriverTask task = driver.discoverStoragePools(driverStorageSystem, driverStoragePools);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // discovery completed
                for (StoragePool storagePool : driverStoragePools) {
                    com.emc.storageos.db.client.model.StoragePool pool;
                    // Check if this storage pool was already discovered
                    String poolNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem, storagePool.getNativeId(),
                            NativeGUIDGenerator.POOL);

                    List<com.emc.storageos.db.client.model.StoragePool> pools =
                            queryActiveResourcesByAltId(_dbClient, com.emc.storageos.db.client.model.StoragePool.class, "nativeGuid", poolNativeGuid);
                    if (pools.isEmpty()) {
                        _log.info("Pool {} is new, native GUID {}", storagePool.getNativeId(), poolNativeGuid);
                        pool = new com.emc.storageos.db.client.model.StoragePool();
                        pool.setId(URIUtil.createId(com.emc.storageos.db.client.model.StoragePool.class));
                        pool.setIsDriverManaged(true);
                        pool.setStorageDevice(accessProfile.getSystemId());
                        pool.setNativeId(storagePool.getNativeId());
                        pool.setNativeGuid(poolNativeGuid);
                        pool.setPoolName(storagePool.getPoolName());
                        pool.addProtocols(storagePool.getProtocols());
                        pool.setPoolServiceType(storagePool.getPoolServiceType());
                        pool.setCompatibilityStatus(storageSystem.getCompatibilityStatus());
                        if (storagePool.getMaximumThickVolumeSize() != null) {
                            pool.setMaximumThickVolumeSize(storagePool.getMaximumThickVolumeSize());
                        } else {
                            pool.setMaximumThickVolumeSize(Long.MAX_VALUE);
                        }
                        if (storagePool.getMinimumThickVolumeSize() != null) {
                            pool.setMinimumThickVolumeSize(storagePool.getMinimumThickVolumeSize());
                        } else {
                            pool.setMinimumThickVolumeSize(0L);
                        }

                        if (storagePool.getMaximumThinVolumeSize() != null) {
                            pool.setMaximumThinVolumeSize(storagePool.getMaximumThinVolumeSize());
                        } else {
                            pool.setMaximumThinVolumeSize(Long.MAX_VALUE);
                        }
                        if (storagePool.getMinimumThinVolumeSize() != null) {
                            pool.setMinimumThinVolumeSize(storagePool.getMinimumThinVolumeSize());
                        } else {
                            pool.setMinimumThinVolumeSize(0L);
                        }

                        pool.setSupportedResourceTypes(storagePool.getSupportedResourceType());
                        pool.setInactive(false);
                        newPools.add(pool);
                    } else if (pools.size() == 1) {
                        _log.info("Pool {} was previously discovered, native GUID {}", storagePool.getNativeId(), poolNativeGuid);
                        pool = pools.get(0);
                        existingPools.add(pool);
                    } else {
                        _log.warn(String.format("There are %d StoragePools with nativeGuid = %s", pools.size(),
                                poolNativeGuid));
                        continue;
                    }

                    // applicable to new and existing storage pools
                    pool.setSubscribedCapacity(storagePool.getSubscribedCapacity());
                    pool.setFreeCapacity(storagePool.getFreeCapacity());
                    pool.setTotalCapacity(storagePool.getTotalCapacity());
                    pool.setOperationalStatus(storagePool.getOperationalStatus());
                    pool.addDriveTypes(storagePool.getSupportedDriveTypes());
                    pool.addSupportedRaidLevels(storagePool.getSupportedRaidLevels());
                    pool.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
                    
                    // Discover the auto tiering policies supported by the storage pool.
                    discoverAutoTieringPoliciesForStoragePool(driverStorageSystem, storagePool, pool,
                            autoTieringPolicyPoolMap, autoTieringPolicyPropertiesMap);     
                    
                    // Discover deduplication capability for storage pool.
                    discoverDeduplicationCapabilityForStoragePool(driverStorageSystem, storagePool, pool);
                    // Discover volume compression capability for storage pool
                    discoverCompressionCapabilityForStoragePool(driverStorageSystem, storagePool, pool);
                }

                // Now that all storage pools have been process we can create or update
                // as necessary the auto tiering policy instances in the controller.
                createOrUpdateAutoTierPolicies(storageSystem, autoTieringPolicyPoolMap, autoTieringPolicyPropertiesMap);

                _log.info("No of newly discovered pools {}", newPools.size());
                _log.info("No of existing discovered pools {}", existingPools.size());

                _dbClient.createObject(newPools);
                _dbClient.updateObject(existingPools);
                allPools.addAll(newPools);
                allPools.addAll(existingPools);
            } else {
                String errorMsg = String.format("Failed to discover storage pools for system %s of type %s . \n" +
                                " Driver task message: %s",
                        accessProfile.getSystemId(), accessProfile.getSystemType(), task.getMessage());
                storageSystem.setLastDiscoveryStatusMessage(errorMsg);
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
            String message = String.format("Storage pools of storage array %s with native id %s were discovered successfully.",
                    storageSystem.getId(), storageSystem.getNativeGuid());
            _log.info(message);
            return allPools;
        } catch (Exception e) {
                String message = String.format("Failed to discover storage pools of storage array %s with native id %s : %s .",
                        storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            storageSystem.setLastDiscoveryStatusMessage(message);
            throw e;
        } finally {
            _dbClient.updateObject(storageSystem);
            _log.info("Discovery of storage pools of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }

    }
    

	/**
     * Discovers the auto tiering policies supported by the passed driver storage pool
     * and updates the passed auto tiering policy maps.
     * 
     * @param driverStorageSystem A reference to the driver storage system.
     * @param driverPool A reference to the driver storage pool.
     * @param pool A reference to the controller storage pool representing the driver storage pool.
     * @param autoTieringPolicyPoolMap A map of unique policy ids and controller storage pools that support the policy.
     * @param autoTieringPolicyPropertiesMap A map of unique policy ids and the policy properties.
     */
    private void discoverAutoTieringPoliciesForStoragePool(StorageSystem driverStorageSystem, StoragePool driverPool,
            com.emc.storageos.db.client.model.StoragePool pool,
            Map<String, List<com.emc.storageos.db.client.model.StoragePool>> autoTieringPolicyPoolMap,
            Map<String, Map<String, List<String>>> autoTieringPolicyPropertiesMap) {
        
        // Get the capabilities specified for the storage pool and
        // process any auto tiering policy capabilities.
        List<CapabilityInstance> capabilities = driverPool.getCapabilities();
        if (capabilities == null) {
            return;
        }
        for (CapabilityInstance capability : capabilities) {
            String capabilityDefinitionUid = capability.getCapabilityDefinitionUid();
            _log.info(String.format("Processing storage capability %s of type %s for storage pool %s on system %s",
                    capability.getName(), capabilityDefinitionUid, driverPool.getNativeId(),
                    driverStorageSystem.getNativeId()));
            // Handle auto tiering policy capability.
            if (isValidCapabilityInstance(capability) && AutoTieringPolicyCapabilityDefinition.CAPABILITY_UID.equals(capabilityDefinitionUid)) {
                // Get the policy id.
                String policyId = capability.getPropertyValue(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.POLICY_ID.name());
                if (policyId == null) {
                    _log.error(String.format("Skipping auto tiering policy capability %s with no policy id for storage pool %s on system %s",
                            capability.getName(), driverPool.getNativeId(), driverStorageSystem.getNativeId()));
                    continue;
                }
                
                // Add the pool to the set of storage pools for this auto tiering policy.
                if (autoTieringPolicyPoolMap.containsKey(policyId)) {
                    List<com.emc.storageos.db.client.model.StoragePool> autoTieringPolicyPools = autoTieringPolicyPoolMap.get(policyId);
                    autoTieringPolicyPools.add(pool);
                } else {
                    List<com.emc.storageos.db.client.model.StoragePool> autoTieringPolicyPools = new ArrayList<>();
                    autoTieringPolicyPools.add(pool);
                    autoTieringPolicyPoolMap.put(policyId, autoTieringPolicyPools);
                }
                
                // Also, save the properties for this auto tiering policy.
                if (!autoTieringPolicyPropertiesMap.containsKey(policyId)) {
                    autoTieringPolicyPropertiesMap.put(policyId, capability.getProperties());
                }
            } 
        } 
    }

    /**
     * Discover deduplication capability for storage pool.
     * If driver does not report "deduplication" for storage pool, we assume that deduplication is disabled.
     * If driver reports "deduplication" for storage pool, we assume that it is enabled, unless its ENABLED property is set to false.
     *
     * @param driverStorageSystem A reference to the driver storage system.
     * @param driverPool A reference to the driver storage pool.
     * @param dbPool A reference to the system storage pool representing the driver storage pool.
     */
    private void discoverDeduplicationCapabilityForStoragePool(StorageSystem driverStorageSystem,
                                                               StoragePool driverPool, com.emc.storageos.db.client.model.StoragePool dbPool) {

        // Get the capabilities specified for the storage pool and process deduplication capability if reported by driver
        List<CapabilityInstance> capabilities = driverPool.getCapabilities();
        if (capabilities == null) {
            return;
        }
        for (CapabilityInstance capability : capabilities) {
            String capabilityDefinitionUid = capability.getCapabilityDefinitionUid();
            _log.info(String.format("Processing storage capability %s of type %s for storage pool %s on system %s",
                    capability.getName(), capabilityDefinitionUid, driverPool.getNativeId(),
                    driverStorageSystem.getNativeId()));
            if (isValidCapabilityInstance(capability) && DeduplicationCapabilityDefinition.CAPABILITY_UID.equals(capabilityDefinitionUid)) {
                // Handle dedup capability.
                // Check if dedup is enabled; we assume that if driver reports deduplication in pool capabilities,
                // it is enabled by default, unless it is explicitly disabled.
                String isEnabled = capability.getPropertyValue(DeduplicationCapabilityDefinition.PROPERTY_NAME.ENABLED.name());
                if (isEnabled != null && isEnabled.equalsIgnoreCase("false") ) {
                    _log.info(String.format("StoragePool %s of storage system %s has deduplication disabled",
                            driverPool.getNativeId(), driverStorageSystem.getNativeId()));
                    dbPool.setDedupCapable(false);
                } else {
                    _log.info(String.format("Enable deduplication for StoragePool %s of storage system %s ",
                            driverPool.getNativeId(), driverStorageSystem.getNativeId()));
                    dbPool.setDedupCapable(true);
                }
            }
        }
    }

    /**
     * Discover volume compression capability for storage pool.
     * If driver does not report "volume compression" for storage pool, we assume that volume compression is disabled.
     * If driver reports "volume compression" for storage pool, we assume that it is enabled, unless its ENABLED property is set to false.
     *
     * @param driverStorageSystem A reference to the driver storage system.
     * @param driverPool A reference to the driver storage pool.
     * @param dbPool A reference to the system storage pool representing the driver storage pool.
     */
    private void discoverCompressionCapabilityForStoragePool(StorageSystem driverStorageSystem,
                                                               StoragePool driverPool, com.emc.storageos.db.client.model.StoragePool dbPool) {

        // Get the capabilities specified for the storage pool and process compression capability if reported by driver
        List<CapabilityInstance> capabilities = driverPool.getCapabilities();
        if (capabilities == null) {
            return;
        }
        for (CapabilityInstance capability : capabilities) {
            String capabilityDefinitionUid = capability.getCapabilityDefinitionUid();
            _log.info(String.format("Processing storage capability %s of type %s for storage pool %s on system %s",
                    capability.getName(), capabilityDefinitionUid, driverPool.getNativeId(),
                    driverStorageSystem.getNativeId()));
            if (isValidCapabilityInstance(capability) && VolumeCompressionCapabilityDefinition.CAPABILITY_UID.equals(capabilityDefinitionUid)) {
                // Handle volume compression capability.
                // Check if volume compression is enabled; we assume that if driver reports compression in pool capabilities,
                // it is enabled by default, unless it is explicitly disabled.
                String isEnabled = capability.getPropertyValue(DeduplicationCapabilityDefinition.PROPERTY_NAME.ENABLED.name());
                if (isEnabled != null && isEnabled.equalsIgnoreCase("false") ) {
                    _log.info(String.format("StoragePool %s of storage system %s has volume compression disabled",
                            driverPool.getNativeId(), driverStorageSystem.getNativeId()));
                    dbPool.setCompressionEnabled(false);
                } else {
                    _log.info(String.format("Enable volume compression for StoragePool %s of storage system %s ",
                            driverPool.getNativeId(), driverStorageSystem.getNativeId()));
                    dbPool.setCompressionEnabled(true);
                }
            }
        }
    }
    
    /**
     * Creates and/or updates the auto tiering policies in the controller database after
     * processing the discovered storage pools and the auto tiering policies that they support.
     * 
     * @param system A reference to the storage system.
     * @param autoTieringPolicyPoolMap A map of the storage pools for each policy keyed by policy id.
     * @param autoTieringPolicyPropertiesMap A map of the auto tiering policy properties keyed by policy id.
     */
    private void createOrUpdateAutoTierPolicies(com.emc.storageos.db.client.model.StorageSystem system, 
            Map<String, List<com.emc.storageos.db.client.model.StoragePool>> autoTieringPolicyPoolMap,
            Map<String, Map<String, List<String>>> autoTieringPolicyPropertiesMap) {
        
        List<DataObject> objectsToCreate = new ArrayList<>();
        List<DataObject> objectsToUpdate = new ArrayList<>();
        for (Entry<String, List<com.emc.storageos.db.client.model.StoragePool>> policyEntry : autoTieringPolicyPoolMap.entrySet()) {
            String policyId = policyEntry.getKey();
            String nativeGuid = NativeGUIDGenerator.generateAutoTierPolicyNativeGuid(system.getNativeGuid(),
                    policyId, NativeGUIDGenerator.AUTO_TIERING_POLICY);
            AutoTieringPolicy autoTieringPolicy = checkAutoTieringPolicyExistsInDB(nativeGuid);
            if (autoTieringPolicy == null) {
                autoTieringPolicy = new AutoTieringPolicy();
                autoTieringPolicy.setId(URIUtil.createId(AutoTieringPolicy.class));
                autoTieringPolicy.setPolicyName(policyId);
                autoTieringPolicy.setStorageSystem(system.getId());
                autoTieringPolicy.setNativeGuid(nativeGuid);
                autoTieringPolicy.setLabel(policyId);
                autoTieringPolicy.setSystemType(system.getSystemType());
                autoTieringPolicy.setPolicyEnabled(Boolean.TRUE);
                Map<String, List<String>> policyProperties = autoTieringPolicyPropertiesMap.get(policyId);
                List<String> provTypeValueList = policyProperties.get(AutoTieringPolicyCapabilityDefinition.PROPERTY_NAME.PROVISIONING_TYPE.name());
                if (!provTypeValueList.isEmpty()) {
                    autoTieringPolicy.setProvisioningType(provTypeValueList.get(0));
                }
                objectsToCreate.add(autoTieringPolicy);
                _log.info(String.format("Creating new auto tiering policy %s, supported by storage pools %s", policyId, policyEntry.getValue()));
            } else {
                objectsToUpdate.add(autoTieringPolicy);
                _log.info(String.format("Updating existing auto tiering policy %s, supported by storage pools %s", policyId, policyEntry.getValue()));
            }
            
            // Set the storage pools for this policy. Since the pools have enabled auto
            // tiering policies, also, make sure auto tiering is enabled on each pool.
            StringSet poolIds = new StringSet();
            for (com.emc.storageos.db.client.model.StoragePool pool : policyEntry.getValue()) {
                poolIds.add(pool.getId().toString());
                // Note that the pool in the db will be updated by the caller.
                pool.setAutoTieringEnabled(true);
            }
            autoTieringPolicy.setPools(poolIds);
            
            // Lastly, since the system has pools with auto tiering enabled, make
            // sure the system has auto tiering enabled.
            if (!system.getAutoTieringEnabled()) {
                system.setAutoTieringEnabled(true);
                _dbClient.updateObject(system);                
            }
        }
        
        // Now any auto tier policies in the database for the passed system that are 
        // not represented by the passed policy map need to be marked disabled.
        disableRemovedAutoTieringPolicies(autoTieringPolicyPoolMap.keySet(), system.getId());
        
        // Lastly create and update objects in the database.
        _dbClient.createObject(objectsToCreate);
        _dbClient.updateObject(objectsToUpdate);
    }
    
    /**
     * Get the auto tiering policy in the database with the passed native GUID if it exists.
     * Otherwise, return null.
     * 
     * @param nativeGuid The native GUI of the auto tiering policy.
     * 
     * @return The auto tiering policy if it exists, null otherwise.
     */
    private AutoTieringPolicy checkAutoTieringPolicyExistsInDB(String nativeGuid) {
        AutoTieringPolicy autoTieringPolicy = null;
        URIQueryResultList queryResult = new URIQueryResultList();
        _dbClient.queryByConstraint(AlternateIdConstraint.Factory.getAutoTieringPolicyByNativeGuidConstraint(nativeGuid), queryResult);
        if (queryResult.iterator().hasNext()) {
            autoTieringPolicy = _dbClient.queryObject(AutoTieringPolicy.class, queryResult.iterator().next());
        }
        return autoTieringPolicy;
    }

    /**
     * Disable any auto tiering policies for the passed system that were not discovered
     * as represented by the passed policy ids.
     * 
     * @param discoveredPolicyIds The ids of the discovered auto tiering policies 
     * after processing all discovered storage pools.
     * @param systemURI The URI of the external storage system.
     */
    private void disableRemovedAutoTieringPolicies(Set<String> discoveredPolicyIds, URI systemURI) {
        List<AutoTieringPolicy> disabledPolicies = new ArrayList<>();
        URIQueryResultList queryResults = new URIQueryResultList();
        _dbClient.queryByConstraint(ContainmentConstraint.Factory.getStorageDeviceFASTPolicyConstraint(systemURI), queryResults);
        Iterator<URI> queryResultsIter = queryResults.iterator();
        while (queryResultsIter.hasNext()) {
            URI autoTieringPolicyURI = queryResultsIter.next();
            AutoTieringPolicy autoTieringPolicy = _dbClient.queryObject(AutoTieringPolicy.class, autoTieringPolicyURI);
            if ((autoTieringPolicy != null) && (!discoveredPolicyIds.contains(autoTieringPolicy.getPolicyName()))) {
                // Disable the policy and clear the supporting storage pools.
                autoTieringPolicy.setPolicyEnabled(false);
                autoTieringPolicy.setPools(new StringSet());
                autoTieringPolicy.setInactive(true);
                disabledPolicies.add(autoTieringPolicy);
            }
        }
        _dbClient.updateObject(disabledPolicies);
    }

    private Map<String, List<com.emc.storageos.db.client.model.StoragePort>> discoverStoragePorts(DiscoveryDriver driver, Set<Network> networksToUpdate,
                                                                                                  AccessProfile accessProfile)
            throws BaseCollectionException {

        URI storageSystemId = accessProfile.getSystemId();
        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, storageSystemId);
        Map<String, List<com.emc.storageos.db.client.model.StoragePort>> storagePorts = new HashMap<>();
        Map<StoragePort, com.emc.storageos.db.client.model.StoragePort> driverPortsToDBPorts = new HashMap<>();

        List<com.emc.storageos.db.client.model.StoragePort> newStoragePorts = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePort> existingStoragePorts = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();

        StorageSystem driverStorageSystem = initStorageSystem(storageSystem);
        // Discover storage ports
        try {
            _log.info("discoverPorts for storage system {} - start", storageSystemId);

            List<StoragePort> driverStoragePorts = new ArrayList<>();
            // Call driver.
            DriverTask task = driver.discoverStoragePorts(driverStorageSystem, driverStoragePorts);
            // todo: need to implement support for async case.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                for (StoragePort driverPort : driverStoragePorts) {
                    com.emc.storageos.db.client.model.StoragePort storagePort = null;
                    String portNativeGuid = NativeGUIDGenerator.generateNativeGuid(
                            storageSystem, driverPort.getNativeId(),
                            NativeGUIDGenerator.PORT);
                    // Check if storage port was already discovered
                    @SuppressWarnings("deprecation")
                    List<URI> portURIs = _dbClient.queryByConstraint(AlternateIdConstraint.Factory.
                            getStoragePortByNativeGuidConstraint(portNativeGuid));
                    for (URI portUri : portURIs) {
                        com.emc.storageos.db.client.model.StoragePort port =
                                _dbClient.queryObject(com.emc.storageos.db.client.model.StoragePort.class, portUri);
                        if (port.getStorageDevice().equals(storageSystemId) && !port.getInactive()) {
                            storagePort = port;
                            break;
                        }
                    }

                    // Verify that discovered port has mandatory identifier "portNetworkId"
                    if (driverPort.getPortNetworkId() == null) {
                        if (storagePort == null) {
                            _log.error("No portNetworkId for new discovered port {}, skip discovery of this port.", portNativeGuid);
                        } else {
                            _log.error("No portNetworkId for previously discovered port {}, skip discovery of this port.", portNativeGuid);
                        }
                        continue;
                    }

                    if (storagePort == null) {
                        // New port processing
                        storagePort = new com.emc.storageos.db.client.model.StoragePort();
                        prepareNewPort(storagePort, driverPort);
                        storagePort.setNativeGuid(portNativeGuid);
                        storagePort.setStorageDevice(storageSystemId);
                        if (driverPort.getNetworkId() != null) {
                            // Get or create Network object for this port
                            Network portNetwork = getNetworkForStoragePort(driverPort);
                            storagePort.setNetwork(portNetwork.getId());
                            // Add endpoint to the network.
                            // Process this for all ports (existing port got a network or changed network cases)
                            // TODO: should we check if existing port was in other network and delete the endpoint from the old network?
                            portNetwork.addEndpoints(new ArrayList<>(Arrays.asList(driverPort.getPortNetworkId())), true);
                            networksToUpdate.add(portNetwork);
                        }
                        storagePort.setTcpPortNumber(driverPort.getTcpPortNumber());
                        storagePort.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.toString());
                        _log.info("Creating new storage port using NativeGuid : {}", portNativeGuid);
                        newStoragePorts.add(storagePort);
                    } else {
                        existingStoragePorts.add(storagePort);
                    }
                    storagePort.setPortNetworkId(driverPort.getPortNetworkId());
                    if (driverPort.getTransportType()!= null &&
                            driverPort.getTransportType().equalsIgnoreCase(StoragePort.TransportType.IP.toString())) {
                        storagePort.setIpAddress(driverPort.getIpAddress());
                    }
                    storagePort.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
                    storagePort.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                    storagePort.setOperationalStatus(driverPort.getOperationalStatus());

                    storagePort.setAvgBandwidth(driverPort.getAvgBandwidth());
                    storagePort.setPortSpeed(driverPort.getPortSpeed());
                    // Set usage metric for the port
                    if (driverPort.getUtilizationMetric() != null) {
                        StringMap usageMetrics = storagePort.getMetrics();
                        MetricsKeys.putDouble(MetricsKeys.portMetric, driverPort.getUtilizationMetric(), usageMetrics);
                        storagePort.setMetrics(usageMetrics);
                    }
                    driverPortsToDBPorts.put(driverPort, storagePort);
                }
                storagePorts.put(NEW, newStoragePorts);
                storagePorts.put(EXISTING, existingStoragePorts);

                // Create storage ha domains for ports
                processStorageHADomains(storageSystem, Collections.unmodifiableMap(driverPortsToDBPorts));
            } else {
                String errorMsg = String.format("Failed to discover storage ports for system %s of type %s. \n" +
                                " Driver task message: %s",
                        accessProfile.getSystemId(), accessProfile.getSystemType(), task.getMessage());
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
            String message = String.format("Storage ports of storage array %s with native id %s were discovered successfully.",
                    storageSystem.getId(), storageSystem.getNativeGuid());
            _log.info(message);
            return storagePorts;
        } catch (Exception e) {
            String message = String.format("Failed to discover storage ports of storage array %s with native id %s : %s .",
                    storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
            _log.error(message, e);
            storageSystem.setLastDiscoveryStatusMessage(message);
            throw e;
        } finally {
            _dbClient.updateObject(storageSystem);
            _log.info("Discovery of storage ports of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }
    }

    private void prepareNewPort(com.emc.storageos.db.client.model.StoragePort storagePort, StoragePort driverPort) {

        storagePort.setId(URIUtil.createId(com.emc.storageos.db.client.model.StoragePort.class));
        storagePort.setIsDriverManaged(true);
        storagePort.setTransportType(driverPort.getTransportType());
        storagePort.setNativeId(driverPort.getNativeId());
        storagePort.setPortName(driverPort.getPortName());
        storagePort.setLabel(driverPort.getPortName());
        storagePort.setPortSpeed(driverPort.getPortSpeed());
        storagePort.setPortGroup(driverPort.getPortGroup());
        if (storagePort.getPortGroup() == null) {
            storagePort.setPortGroup(storagePort.getPortName());
        }
        storagePort.setPortEndPointID(driverPort.getEndPointID());
        _log.info("discoverPort: portNetworkId: {} ", storagePort.getPortNetworkId());
    }

    public static StorageSystem initStorageSystem(com.emc.storageos.db.client.model.StorageSystem storageSystem) {
        // TODO: how to set Site?
        StorageSystem driverStorageSystem = new StorageSystem();
        driverStorageSystem.setNativeId(storageSystem.getNativeId());
        driverStorageSystem.setIpAddress(storageSystem.getIpAddress());
        driverStorageSystem.setSystemName(storageSystem.getLabel());

        return driverStorageSystem;
    }

    /**
     * Returns Network object based on storage port information.
     *
     * @param driverPort [in] - storage port instance
     * @return Network object
     */
    private Network getNetworkForStoragePort(StoragePort driverPort) {
        Network network;
        List<Network> results =
                CustomQueryUtility.queryActiveResourcesByAltId(_dbClient, Network.class, "nativeId", driverPort.getNetworkId());
        if (results == null || results.isEmpty()) {
            network = new Network();
            network.setId(URIUtil.createId(Network.class));
            network.setTransportType(driverPort.getTransportType());
            network.setNativeId(driverPort.getNetworkId());
            network.setLabel(driverPort.getNetworkId());
            network.setRegistrationStatus(DiscoveredDataObject.RegistrationStatus.REGISTERED.name());
            network.setInactive(false);
            _dbClient.createObject(network);
            _log.info("Created a new network {}." , network.getLabel());
        } else {
            network = results.get(0);
        }
        return network;
    }

    private void processStorageHADomains(com.emc.storageos.db.client.model.StorageSystem storageSystem,
                                         Map<StoragePort, com.emc.storageos.db.client.model.StoragePort> driverPortsToDBPorts) {

        // Map ha zone names to driver ports
        Map<String, Set<StoragePort>> haZoneNameToDriverPorts = new HashMap<>();
        Set<StoragePort> driverPorts = driverPortsToDBPorts.keySet();
        for (StoragePort driverPort : driverPorts) {
            if (driverPort.getPortHAZone() != null && !driverPort.getPortHAZone().isEmpty()) {
                Set<StoragePort> haZonePorts = haZoneNameToDriverPorts.get(driverPort.getPortHAZone());
                if (haZonePorts == null) {
                    haZonePorts = new HashSet<>();
                    haZoneNameToDriverPorts.put(driverPort.getPortHAZone(), haZonePorts);
                }
                haZonePorts.add(driverPort);
            }
        }

        for (Map.Entry<String, Set<StoragePort>> haZoneNameToDriverPort : haZoneNameToDriverPorts.entrySet()) {
            String portHAZone = haZoneNameToDriverPort.getKey();
            Set<StoragePort> haZoneDriverPorts = haZoneNameToDriverPort.getValue();
            if (portHAZone != null) {
                String haDomainNativeGUID = NativeGUIDGenerator.generateNativeGuid(storageSystem, portHAZone, NativeGUIDGenerator.ADAPTER);
                _log.info("HA Domain Native Guid : {}", haDomainNativeGUID);
                @SuppressWarnings("deprecation")
                List<URI> uriHaList = _dbClient.queryByConstraint(AlternateIdConstraint.Factory
                        .getStorageHADomainByNativeGuidConstraint(haDomainNativeGUID));
                StorageHADomain haDomain;
                if (uriHaList.isEmpty()) {
                    haDomain = new StorageHADomain();
                    haDomain.setId(URIUtil.createId(StorageHADomain.class));
                    haDomain.setNativeGuid(haDomainNativeGUID);
                    haDomain.setName(portHAZone);
                    haDomain.setAdapterName(portHAZone);
                    haDomain.setStorageDeviceURI(storageSystem.getId());
                    haDomain.setNumberofPorts(String.valueOf(haZoneDriverPorts.size()));
                    _dbClient.createObject(haDomain);
                } else {
                    haDomain = _dbClient.queryObject(StorageHADomain.class, uriHaList.get(0));
                    haDomain.setNumberofPorts(String.valueOf(haZoneDriverPorts.size()));
                    _dbClient.updateObject(haDomain);
                }

                for (StoragePort driverPort : haZoneDriverPorts) {
                    com.emc.storageos.db.client.model.StoragePort port = driverPortsToDBPorts.get(driverPort);
                    port.setStorageHADomain(haDomain.getId());
                }
            }
        }
    }

    private boolean isValidCapabilityInstance(CapabilityInstance capability) {
        // Get the capability definition for the capability.
        String capabilityDefinitionUid = capability.getCapabilityDefinitionUid();
        if ((capabilityDefinitionUid == null) || (capabilityDefinitionUid.isEmpty())) {
            _log.error(String.format(
                    "Capability %s with no capability definition UID.",
                    capability.getName()));
            return false;
        }

        // Get the capability definition from the map of supported
        // capability definitions.
        CapabilityDefinition capabilityDefinition = capabilityDefinitions.get(capabilityDefinitionUid);
        if (capabilityDefinition == null) {
            _log.info(String.format("Skipping unsupported capability %s of type %s ",
                    capability.getName(), capabilityDefinitionUid));
            return false;
        }
        return true;
    }
}
