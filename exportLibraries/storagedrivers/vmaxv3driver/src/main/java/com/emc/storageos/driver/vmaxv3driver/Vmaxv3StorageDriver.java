package com.emc.storageos.driver.vmaxv3driver;

import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.*;

import java.util.List;

/**
 * This class is the entrance of all the operations provided by the SB SDK
 * framework. As suggested by the framework code, an SB SDK driver should
 * extend the "DefaultStorageDriver" class and replace the methods
 * implementations with its own content.
 *
 * Created by gang on 6/15/16.
 */
public class Vmaxv3StorageDriver extends DefaultStorageDriver {

    /**
     * Defined in the "StorageDriver" interface. As the SB SDK author said:
     * The SB SDK support in CoprHD currently does not use this method.
     * This method role will be finalized at x-wing release time. For now
     * empty implementation is fine.
     * @return
     */
    @Override
    public RegistrationData getRegistrationData() {
        return null;
    }

    /**
     * Defined in the "StorageDriver" interface. As the SB SDK author said:
     * This method is used by CoprHD to query asynchronous operations result
     * done by specific drivers which supports asynchronous operations.
     * Framework will never call this method in a case when the driver only
     * supports synchronous operations, such as VMAX V3 REST APIs, so in this
     * driver it returns null.
     * @param taskId
     * @return
     */
    @Override
    public DriverTask getTask(String taskId) {
        return null;
    }

    /**
     * Defined in the "StorageDriver" interface. As the SB SDK author said:
     * This method is used for unmanaged objects discovery. If this have to be
     * supported for this driver, the driver has to implement this method. This
     * is the only use case for now. Otherwise, returning null is fine.
     * @param storageSystemId storage system native id
     * @param objectId object native id
     * @param type  class instance
     * @param <T>
     * @return
     */
    @Override
    public <T extends StorageObject> T getStorageObject(String storageSystemId, String objectId, Class<T> type) {
        return null;
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystems
     * @return
     */
    @Override
    public DriverTask discoverStorageSystem(List<StorageSystem> storageSystems) {
        return null;
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param storagePools
     * @return
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        return null;
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param storagePorts
     * @return
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        return null;
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param embeddedStorageHostComponents
     * @return
     */
    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        return null;
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageProvider
     * @param storageSystems
     * @return
     */
    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        return null;
    }
}
