/*
 * Copyright (c) 2014 EMC Corporation
 * All Rights Reserved
 */

package com.emc.storageos.volumecontroller.impl.externaldevice;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.db.client.DbClient;
import com.emc.storageos.db.client.model.StoragePool;
import com.emc.storageos.db.client.model.StorageSystem;
import com.emc.storageos.db.client.model.Volume;
import com.emc.storageos.exceptions.DeviceControllerException;
import com.emc.storageos.storagedriver.AbstractStorageDriver;
import com.emc.storageos.storagedriver.BlockStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.svcs.errorhandling.model.ServiceError;
import com.emc.storageos.svcs.errorhandling.resources.ServiceCode;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.NativeGUIDGenerator;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;


public class ExternalBlockStorageDevice extends DefaultBlockStorageDevice {

    private Logger _log = LoggerFactory.getLogger(ExternalBlockStorageDevice.class);
    private Map<String, AbstractStorageDriver> drivers;
    private DbClient dbClient;
    private ControllerLockingService locker;
    // Initialized drivers map
    private Map<String, BlockStorageDriver> blockDrivers  = new HashMap<>();


    public void setDbClient(DbClient dbClient) {
        this.dbClient = dbClient;
    }

    public void setLocker(ControllerLockingService locker) {
        this.locker = locker;
    }

    public void setDrivers(Map<String, AbstractStorageDriver> drivers) {
        this.drivers = drivers;
    }

    private BlockStorageDriver getDriver(String driverType) {
        // look up driver
        BlockStorageDriver discoveryDriver = blockDrivers.get(driverType);
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
            blockDrivers.put(driverType, driver);
            return driver;
        }
    }

    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance();
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(locker);
        driver.setLockManager(lockManager);
    }



        /**
         *  {@inheritDoc}
         *
         */
    @Override
    public void doCreateVolumes(StorageSystem storageSystem, StoragePool storagePool,
                                String opId, List<Volume> volumes,
                                VirtualPoolCapabilityValuesWrapper capabilities,
                                TaskCompleter taskCompleter) throws DeviceControllerException {

        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        if (driver == null) {
            String errorMsg = String.format("No driver entry defined for device type: %s . ", storageSystem.getSystemType());
            _log.info(errorMsg);
            //  todo revisit
            throw new ExternalDeviceCollectionException(false, ServiceCode.DISCOVERY_ERROR,
                    null, errorMsg, null, null);
        }

        List<StorageVolume> driverVolumes = new ArrayList<>();
        Map<StorageVolume, Volume> driverVolumeToVolumeMap = new HashMap<>();

        try {
            for (Volume volume : volumes) {
                StorageVolume driverVolume = new StorageVolume();
                driverVolume.setStorageSystemId(storageSystem.getNativeId());
                driverVolume.setStoragePoolId(storagePool.getNativeId());
                driverVolume.setRequestedCapacity(volume.getCapacity());
                driverVolume.setThinlyProvisioned(volume.getThinlyProvisioned());
                // Todo complete attribute setting.

                driverVolumes.add(driverVolume);
                driverVolumeToVolumeMap.put(driverVolume, volume);
            }

            DriverTask task = driver.createVolumes(driverVolumes, null);
            // TODO: this is short cut for now, assuming synchronous driver implementation
            // We will implement support for async case later.
            if (task.getStatus() == DriverTask.TaskStatus.READY) {
                updateVolumesWithDriverVolumeInfo(dbClient, driverVolumeToVolumeMap);
                dbClient.updateObject(driverVolumeToVolumeMap.values());
                _log.info("Calling task completer....");
                taskCompleter.ready(dbClient);
            } else {
                // failed
                // TODO support async
                // Set volumes to inactive state
                List<URI> volumeURIs = new ArrayList<>();
                for (Volume volume : volumes) {
                    volume.setInactive(true);
                    volumeURIs.add(volume.getId());
                }
                String errorMsg = String.format("doCreateVolumes -- Failed to create volumes: %s .", task.getMessage());
                _log.error(errorMsg);
                ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed(volumeURIs.toString(), errorMsg);
                taskCompleter.error(dbClient, serviceError);
            }
        } catch (IOException e) {
            _log.error("doCreateVolumes -- Failed to create volumes. ", e);
            ServiceError serviceError = ExternalDeviceException.errors.createVolumesFailed("doCreateVolumes", e.getMessage());
            taskCompleter.error(dbClient, serviceError);
        }
    }

    /**
     *  {@inheritDoc}
     *
     */
    @Override
    public void doExpandVolume(StorageSystem storageSystem, StoragePool storagePool,
                               Volume volume, Long size, TaskCompleter taskCompleter)
            throws DeviceControllerException {

    }

    /**
     * {@inheritDoc}
     *
     */
    @Override
    public void doDeleteVolumes(StorageSystem storageSystem, String opId,
                                List<Volume> volumes, TaskCompleter taskCompleter) throws DeviceControllerException {

    }

    @Override
    public void doConnect(StorageSystem storageSystem) {
        BlockStorageDriver driver = getDriver(storageSystem.getSystemType());
        if (driver == null) {
            throw DeviceControllerException.exceptions.connectStorageFailedNoDevice(
                    storageSystem.getSystemType());
        }
        _log.info("doConnect to external device {} - start", storageSystem.getId());
        _log.info("doConnect to external device {} - end", storageSystem.getId());
    }


    private void updateVolumesWithDriverVolumeInfo(DbClient dbClient, Map<StorageVolume, Volume> driverVolumesMap)
                  throws IOException {
        for (Map.Entry driverVolumeToVolume : driverVolumesMap.entrySet()) {
            StorageVolume driverVolume = (StorageVolume)driverVolumeToVolume.getKey();
            Volume volume = (Volume)driverVolumeToVolume.getValue();
            volume.setNativeId(driverVolume.getNativeId());
            volume.setDeviceLabel(driverVolume.getDeviceLabel());
            volume.setNativeGuid(NativeGUIDGenerator.generateNativeGuid(dbClient, volume));

            if (driverVolume.getWwn() != null) {
                volume.setWWN(String.format("%s%s", driverVolume.getStorageSystemId(), driverVolume.getNativeId()));
            }
            volume.setProvisionedCapacity(driverVolume.getProvisionedCapacity());
            volume.setAllocatedCapacity(driverVolume.getAllocatedCapacity());
        }
    }

}
