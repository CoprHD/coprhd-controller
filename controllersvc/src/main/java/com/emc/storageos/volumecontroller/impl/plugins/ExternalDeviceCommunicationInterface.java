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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.URIUtil;
import com.emc.storageos.db.client.constraint.AlternateIdConstraint;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.db.client.model.Network;
import com.emc.storageos.db.client.model.StringMap;
import com.emc.storageos.db.client.model.StringSet;
import com.emc.storageos.db.client.util.CustomQueryUtility;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.StoragePortAssociationHelper;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceCollectionException;
import com.emc.storageos.volumecontroller.impl.plugins.metering.smis.processor.MetricsKeys;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

public class ExternalDeviceCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {

    private static final String NEW = "new";
    private static final String EXISTING = "existing";
    private Logger _log = LoggerFactory.getLogger(ExternalDeviceCommunicationInterface.class);
    private Map<String, AbstractStorageDriver> _drivers;

    // Initialized drivers map
    private Map<String, DiscoveryDriver> discoveryDrivers = new HashMap<>();

    public void setDrivers(Map<String, AbstractStorageDriver> drivers) {
        _drivers = drivers;
    }

    private synchronized DiscoveryDriver getDriver(String driverType) {
        // look up driver
        DiscoveryDriver discoveryDriver = discoveryDrivers.get(driverType);
        if (discoveryDriver != null) {
            return discoveryDriver;
        } else {
            // init driver
            AbstractStorageDriver driver = _drivers.get(driverType);
            if (driver == null) {
                _log.info("No driver entry defined for device type: {} . ", driverType);
                return null;
            }
            init(driver);
            discoveryDrivers.put(driverType, driver);
            return driver;
        }
    }


    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance(_dbClient);
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(_locker);
        driver.setLockManager(lockManager);
    }

    @Override
    public void collectStatisticsInformation(AccessProfile accessProfile) throws BaseCollectionException {

    }

    @Override
    public void scan(AccessProfile accessProfile) throws BaseCollectionException {

    }

    @Override
    public void discover(AccessProfile accessProfile) throws BaseCollectionException {

        // Get discovery driver class based on storage device type
        String deviceType = accessProfile.getSystemType();
        DiscoveryDriver driver = getDriver(deviceType);
        if (driver == null) {
            String errorMsg = String.format("No driver entry defined for device type: %s . ", deviceType);
            _log.info(errorMsg);
            throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                    null, errorMsg, null, null);
        }

        try {
            // discover storage system
            discoverStorageSystem(driver, accessProfile);
            _completer.statusPending(_dbClient, "Completed storage system discovery");

            // discover storage pools
            List<com.emc.storageos.db.client.model.StoragePool> storagePools = discoverStoragePools(driver, accessProfile);
            List<com.emc.storageos.db.client.model.StoragePool> storagePoolsToMatchWithVpools = new ArrayList();
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
        } catch (BaseCollectionException bEx) {
            _completer.error(_dbClient, bEx);
        } catch (Exception ex) {
            _completer.error(_dbClient, null);
        }
    }

    private void discoverStorageSystem(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {

        StorageSystem driverStorageSystem = new StorageSystem();
        driverStorageSystem.setIpAddress(accessProfile.getIpAddress());
        driverStorageSystem.setPortNumber(accessProfile.getPortNumber());
        driverStorageSystem.setUsername(accessProfile.getUserName());
        driverStorageSystem.setPassword(accessProfile.getPassword());
        List<StorageSystem> driverStorageSystems = Collections.singletonList(driverStorageSystem);

        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());
        // TODO: temporary to identify storage system by name when managed by provider.
        driverStorageSystem.setSystemName(storageSystem.getLabel());

        try {
            _log.info("discoverStorageSystem information for storage system {}, name {} - start",
                    accessProfile.getSystemId(), driverStorageSystem.getSystemName());
            DriverTask task = driver.discoverStorageSystem(driverStorageSystems);

            // check task status and monitor until completion.
            // TODO: this is short cut for now, assuming synchronous driver implementation
            // We will implement support for async case later.
            // process discovery results.
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
                // failed
                // TODO support async
                storageSystem.setReachableStatus(false);
                String errorMsg = String.format("Failed to discover storage system %s of type %s",
                       accessProfile.getSystemId(), accessProfile.getSystemType());
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
            String message = String.format("Storage array %s with native id %s was discovered successfully.",
                    storageSystem.getId(), storageSystem.getNativeGuid());
            storageSystem.setLastDiscoveryStatusMessage(message);
        } catch (Exception e) {
            if (storageSystem != null) {
                String message = String.format("Failed to discover storage array %s with native id %s : %s .",
                        storageSystem.getId(), storageSystem.getNativeGuid(), e.getMessage());
                storageSystem.setLastDiscoveryStatusMessage(message);
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
            // Support only sync discovery at this moment.
            // TODO support async
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
                        pool.setMaximumThickVolumeSize(storagePool.getMaximumThickVolumeSize());
                        pool.setMinimumThickVolumeSize(storagePool.getMinimumThickVolumeSize());
                        pool.setMaximumThinVolumeSize(storagePool.getMaximumThinVolumeSize());
                        pool.setMinimumThinVolumeSize(storagePool.getMinimumThinVolumeSize());
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
                // driver task is not ready
                // TODO support async
                String errorMsg = String.format("Failed to discover storage pools for system %s of type %s",
                        accessProfile.getSystemId(), accessProfile.getSystemType());
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
            _log.info(message);
            throw e;
        } finally {
            _log.info("Discovery of storage pools of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }

    }

    private Map<String, List<com.emc.storageos.db.client.model.StoragePort>> discoverStoragePorts(DiscoveryDriver driver, Set<Network> networksToUpdate,
                                                                                                  AccessProfile accessProfile)
            throws BaseCollectionException {

        URI storageSystemId = accessProfile.getSystemId();
        com.emc.storageos.db.client.model.StorageSystem storageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, storageSystemId);
        HashMap<String, List<com.emc.storageos.db.client.model.StoragePort>> storagePorts = new HashMap<>();

        List<com.emc.storageos.db.client.model.StoragePort> newStoragePorts = new ArrayList<>();
        List<com.emc.storageos.db.client.model.StoragePort> existingStoragePorts = new ArrayList<>();
        List<String> endpoints = new ArrayList<>();

        StorageSystem driverStorageSystem = initStorageSystem(storageSystem);
        // Discover storage ports
        try {
            _log.info("discoverPorts for storage system {} - start", storageSystemId);

            List<StoragePort> driverStoragePorts = new ArrayList<>();
            DriverTask task = driver.discoverStoragePorts(driverStorageSystem, driverStoragePorts);
            // Support only sync discovery at this moment.
            // TODO support async
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                // discovery completed

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
                        storagePort.setId(URIUtil.createId(com.emc.storageos.db.client.model.StoragePort.class));
                        storagePort.setIsDriverManaged(true);
                        storagePort.setTransportType(driverPort.getTransportType());
                        storagePort.setNativeGuid(portNativeGuid);
                        storagePort.setNativeId(driverPort.getNativeId());
                        storagePort.setStorageDevice(storageSystemId);
                        storagePort.setPortName(driverPort.getPortName());
                        storagePort.setLabel(driverPort.getPortName());
                        storagePort.setPortSpeed(driverPort.getPortSpeed());
                        storagePort.setPortGroup(driverPort.getPortGroup());
                        if (storagePort.getPortGroup() == null) {
                            storagePort.setPortGroup(storagePort.getPortName());
                        }
                        storagePort.setPortEndPointID(driverPort.getEndPointID());
                        _log.info("discoverPort: portNetworkId: {} ", storagePort.getPortNetworkId());
                        if (driverPort.getNetworkId() != null) {
                            // Get or create Network object for this port
                            Network portNetwork = getNetworkForStoragePort(driverPort);
                            storagePort.setNetwork(portNetwork.getId());
                            // Add endpoint to the network.
                            // TODO we move this to process for all ports (existing port got a network or changed network cases)
                            // TODO and should we check if existing port was in other network and delete the endpoint from the old network?
                            portNetwork.addEndpoints(new ArrayList<String>(Arrays.asList(driverPort.getPortNetworkId())), true);
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
                }
                storagePorts.put(NEW, newStoragePorts);
                storagePorts.put(EXISTING, existingStoragePorts);
            } else {
                // driver task is not ready
                // TODO support async
                String errorMsg = String.format("Failed to discover storage ports for system %s of type %s",
                        accessProfile.getSystemId(), accessProfile.getSystemType());
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
            _log.info(message);
            throw e;
        } finally {
            _log.info("Discovery of storage ports of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }
    }

    private StorageSystem initStorageSystem(com.emc.storageos.db.client.model.StorageSystem storageSystem) {
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
}
