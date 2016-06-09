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
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.emc.storageos.xtremio.restapi.errorhandling.XtremIOApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StorageHADomain;
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
                _log.info("No driver entry defined for device type: {} . ", driverType);
                return null;
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
            Boolean useSsl = storageProvider.getUseSSL();
            String msg = String.format("Storage provider info: host: %s, port: %s, user: %s, useSsl: %s", hostName, portNumber, username, useSsl);
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
                    String nativeGuid = NativeGUIDGenerator.generateNativeGuid(accessProfile.getSystemType(),
                            driverStorageSystem.getNativeId());
                    StorageSystemViewObject storageSystemView = storageSystemsCache.get(nativeGuid);
                    if (storageSystemView == null) {
                        storageSystemView = new StorageSystemViewObject();
                    }
                    storageSystemView.setDeviceType(systemType);
                    storageSystemView.addprovider(accessProfile.getSystemId().toString());
                    storageSystemView.setProperty(StorageSystemViewObject.SERIAL_NUMBER, driverStorageSystem.getSerialNumber());
                    storageSystemView.setProperty(StorageSystemViewObject.VERSION, driverStorageSystem.getFirmwareVersion());
                    storageSystemView.setProperty(StorageSystemViewObject.STORAGE_NAME, nativeGuid);
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
        } catch (Exception ex) {
            _completer.error(_dbClient, null);
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
                unManagedVolumeDiscoverer.discoverUnManagedBlockObjects((BlockStorageDriver)driver, storageSystem, _dbClient, _partitionManager);
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

        StorageSystem driverStorageSystem = new StorageSystem();
        driverStorageSystem.setIpAddress(accessProfile.getIpAddress());
        driverStorageSystem.setPortNumber(accessProfile.getPortNumber());
        driverStorageSystem.setUsername(accessProfile.getUserName());
        driverStorageSystem.setPassword(accessProfile.getPassword());

        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());
        // TODO: temporary label is used to identify storage system by name when multiple systems are managed by provider at
        // the provided endpoint.
        driverStorageSystem.setSystemName(storageSystem.getLabel());

        try {
            _log.info("discoverStorageSystem information for storage system {}, name {} - start",
                    accessProfile.getSystemId(), driverStorageSystem.getSystemName());
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
        List<StoragePool> driverStoragePools = new ArrayList<>();
        // Discover storage pools
        List<com.emc.storageos.db.client.model.StoragePool> allPools = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePool> newPools = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePool> existingPools = new ArrayList<>();

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
                        pool.setDiscoveryStatus(DiscoveredDataObject.DiscoveryStatus.VISIBLE.name());
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

                }
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
}
