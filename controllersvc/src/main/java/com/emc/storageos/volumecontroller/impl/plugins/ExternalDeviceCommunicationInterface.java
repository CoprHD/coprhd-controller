package com.emc.storageos.volumecontroller.impl.plugins;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.emc.storageos.storagedriver.model.StoragePool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.DiscoveredDataObject;
import com.emc.storageos.plugins.AccessProfile;
import com.emc.storageos.plugins.BaseCollectionException;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.DiscoveryDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StorageSystem;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.externaldevice.ExternalDeviceCollectionException;
import com.emc.storageos.volumecontroller.impl.utils.DiscoveryUtils;

public class ExternalDeviceCommunicationInterface extends
        ExtendedCommunicationInterfaceImpl {

    private Logger _log = LoggerFactory.getLogger(ExternalDeviceCommunicationInterface.class);
    private Map<String, AbstractStorageDriver> _drivers;
    private DbClient _dbClient;

    // Initialized drivers map
    private Map<String, DiscoveryDriver> discoveryDrivers;


    private DiscoveryDriver getDriver(String driverType) {
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
        // TODO: temp code, may need to initialize this same way we initialize all service objects for communication interfaces, through
        // TODO: inject() methods. For now do it here to minimize changes in controller-conf.xml
        Registry driverRegistry = RegistryImpl.getInstance();
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
            _log.info("No driver entry defined for device type: {} . ", deviceType);
            return;
        }

        try {
            // discover storage system
            discoverStorageSystem(driver, accessProfile);
            _completer.statusPending(_dbClient, "Completed storage system discovery");
            discoverStoragePools(driver, accessProfile);
            _completer.statusPending(_dbClient, "Completed storage pools discovery");
            discoverStoragePorts(driver, accessProfile);
            _completer.statusReady(_dbClient, "Completed storage discovery");
        } catch (BaseCollectionException bEx) {
            _completer.error(_dbClient, bEx);
        } catch (Exception ex) {
            _completer.error(_dbClient, null);
        }
    }

    private void discoverStorageSystem(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {
        StorageSystem storageSystem = new StorageSystem();
        storageSystem.setIpAddress(accessProfile.getIpAddress());
        storageSystem.setPortNumber(accessProfile.getPortNumber());
        storageSystem.setUsername(accessProfile.getUserName());
        storageSystem.setPassword(accessProfile.getPassword());
        List<StorageSystem> storageSystems = Collections.singletonList(storageSystem);
        com.emc.storageos.db.client.model.StorageSystem internalStorageSystem =
                _dbClient.queryObject(com.emc.storageos.db.client.model.StorageSystem.class, accessProfile.getSystemId());
        try {
            _log.info("discoverStorageSystem information for storage system {} - start", accessProfile.getSystemId());
            DriverTask task = driver.discoverStorageSystem(storageSystems);

            // check task status and monitor until completion.
            // TODO: this is short cut for now, assuming synchronous driver implementation
            // We will implement support for async case later.
            // process discovery results.
            if (task.getStatus() == DriverTask.TaskStatus.READY)  {
                // discovery completed
                internalStorageSystem.setSerialNumber(storageSystem.getSerialNumber());
                internalStorageSystem.setNativeId(storageSystem.getNativeId());
                String nativeGuid = NativeGUIDGenerator.generateNativeGuid(accessProfile.getSystemType(),
                        storageSystem.getNativeId());
                internalStorageSystem.setNativeGuid(nativeGuid);
                internalStorageSystem.setFirmwareVersion(storageSystem.getFirmwareVersion());
                if (storageSystem.isSupportedVersion()) {
                    internalStorageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.COMPATIBLE.name());
                    internalStorageSystem.setReachableStatus(true);
                } else {
                    internalStorageSystem.setCompatibilityStatus(DiscoveredDataObject.CompatibilityStatus.INCOMPATIBLE.name());
                    internalStorageSystem.setReachableStatus(false);
                    DiscoveryUtils.setSystemResourcesIncompatible(_dbClient, _coordinator, internalStorageSystem.getId());
                    String errorMsg = String.format("Storage array %s has firmware version %s which is not supported by driver",
                            internalStorageSystem.getNativeGuid(), internalStorageSystem.getFirmwareVersion());
                    throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                            null, errorMsg, null, null);
                }
            } else {
                // failed
                internalStorageSystem.setReachableStatus(false);
                String errorMsg = String.format("Failed to discover storage system %s of type %s",
                       accessProfile.getSystemId(), accessProfile.getSystemType());
                throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                        null, errorMsg, null, null);
            }
            String message = String.format("Storage array %s with native id %s was discovered successfully.",
                    internalStorageSystem.getId(), internalStorageSystem.getNativeGuid());
            internalStorageSystem.setLastDiscoveryStatusMessage(message);
        } catch (Exception e) {
            if (internalStorageSystem != null) {
                String message = String.format("Failed to discover storage array %s with native id %s : %s .",
                        internalStorageSystem.getId(), internalStorageSystem.getNativeGuid(), e.getMessage());
                internalStorageSystem.setLastDiscoveryStatusMessage(message);
            }
            throw e;
        } finally {
            if (internalStorageSystem != null) {
                _dbClient.persistObject(internalStorageSystem);
            }
            _log.info("Discovery of storage system {} of type {} - end", accessProfile.getSystemId(), accessProfile.getSystemType());
        }
    }

    private void discoverStoragePools(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {
        List<StoragePool> storagePools = new ArrayList<StoragePool>();


    }

    private void discoverStoragePorts(DiscoveryDriver driver, AccessProfile accessProfile)
            throws BaseCollectionException {

    }
}
