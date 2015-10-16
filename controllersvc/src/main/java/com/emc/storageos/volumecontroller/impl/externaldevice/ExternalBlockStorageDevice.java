package com.emc.storageos.volumecontroller.impl.externaldevice;

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
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.impl.LockManagerImpl;
import com.emc.storageos.storagedriver.impl.RegistryImpl;
import com.emc.storageos.volumecontroller.ControllerLockingService;
import com.emc.storageos.volumecontroller.DefaultBlockStorageDevice;
import com.emc.storageos.volumecontroller.TaskCompleter;
import com.emc.storageos.volumecontroller.impl.utils.VirtualPoolCapabilityValuesWrapper;


public class ExternalBlockStorageDevice extends DefaultBlockStorageDevice {

    private Logger _log = LoggerFactory.getLogger(ExternalBlockStorageDevice.class);
    private Map<String, AbstractStorageDriver> _drivers;
    private DbClient _dbClient;
    private ControllerLockingService _locker;
    // Initialized drivers map
    private Map<String, BlockStorageDriver> blockDrivers;


    private BlockStorageDriver getDriver(String driverType) {
        // look up driver
        BlockStorageDriver discoveryDriver = blockDrivers.get(driverType);
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
            blockDrivers.put(driverType, driver);
            return driver;
        }
    }

    private void init(AbstractStorageDriver driver) {
        Registry driverRegistry = RegistryImpl.getInstance();
        driver.setDriverRegistry(driverRegistry);
        LockManager lockManager = LockManagerImpl.getInstance(_locker);
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

}
