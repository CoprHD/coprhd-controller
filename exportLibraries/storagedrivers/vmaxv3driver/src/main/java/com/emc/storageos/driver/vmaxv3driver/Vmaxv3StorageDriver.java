package com.emc.storageos.driver.vmaxv3driver;

import com.emc.storageos.driver.vmaxv3driver.base.Operation;
import com.emc.storageos.driver.vmaxv3driver.base.OperationFactory;
import com.emc.storageos.driver.vmaxv3driver.base.OperationFactoryImpl;
import com.emc.storageos.storagedriver.DefaultStorageDriver;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.RegistrationData;
import com.emc.storageos.storagedriver.model.StorageHostComponent;
import com.emc.storageos.storagedriver.model.StorageObject;
import com.emc.storageos.storagedriver.model.StoragePool;
import com.emc.storageos.storagedriver.model.StoragePort;
import com.emc.storageos.storagedriver.model.StorageProvider;
import com.emc.storageos.storagedriver.model.StorageSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * This class is the entrance of all the operations provided by the SB SDK
 * framework. As suggested by the framework code, an SB SDK driver should
 * extend the "DefaultStorageDriver" class and replace the methods
 * implementations with its own content.
 *
 * Created by gang on 6/15/16.
 */
public class Vmaxv3StorageDriver extends DefaultStorageDriver {

    private static final Logger logger = LoggerFactory.getLogger(Vmaxv3StorageDriver.class);

    private OperationFactory operationFactory;

    public Vmaxv3StorageDriver() {
        this.operationFactory = new OperationFactoryImpl();
    }

    /**
     * Perform the required operation of VMAX V3 driver.
     *
     * @param operationName The operation name.
     * @param parameters The operation parameters.
     * @return A DriverTask instance to indicate the operation result.
     */
    protected DriverTask execute(String operationName, Object... parameters) {
        logger.debug("Vmaxv3StorageDriver executes '{}' operation starts, arguments are: {}",
            operationName, parameters);
        // Get Operation instance.
        Operation operation = this.operationFactory.getInstance(this.driverRegistry, this.lockManager,
            operationName, parameters);
        if(operation == null) {
            logger.warn("Vmaxv3StorageDriver '{}' operation is not supported.", operationName);
            return null;
        }
        // Prepare Task instance.
        String taskId = String.format("%s+%s+%s", Vmaxv3Constants.DRIVER_NAME, operationName, UUID.randomUUID());
        DriverTask task = new Vmaxv3DriverTask(taskId);
        task.setStartTime(Calendar.getInstance());
        // Do the actual work.
        Map<String, Object> result = operation.execute();
        task.setEndTime(Calendar.getInstance());
        // Prepare result.
        if((Boolean)result.get("success")) {
            task.setStatus(DriverTask.TaskStatus.READY);
        } else {
            task.setStatus((Boolean)result.get("partially_failed") ? DriverTask.TaskStatus.PARTIALLY_FAILED :
                DriverTask.TaskStatus.FAILED);
            String message = String.format("Vmaxv3StorageDriver: Unable to perform operation '%s', error: %s.\n",
                operationName, result.get("message"));
            task.setMessage(message);
        }
        logger.debug("Vmaxv3StorageDriver executes '{}' operation ends, result is: {}", task);
        return task;
    }

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
        return this.execute("discoverStorageSystem", storageSystems);
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param storagePools
     * @return
     */
    @Override
    public DriverTask discoverStoragePools(StorageSystem storageSystem, List<StoragePool> storagePools) {
        return this.execute("discoverStoragePools", storageSystem, storagePools);
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param storagePorts
     * @return
     */
    @Override
    public DriverTask discoverStoragePorts(StorageSystem storageSystem, List<StoragePort> storagePorts) {
        return this.execute("discoverStoragePorts", storageSystem, storagePorts);
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageSystem
     * @param embeddedStorageHostComponents
     * @return
     */
    @Override
    public DriverTask discoverStorageHostComponents(StorageSystem storageSystem, List<StorageHostComponent> embeddedStorageHostComponents) {
        return this.execute("discoverStorageHostComponents", storageSystem, embeddedStorageHostComponents);
    }

    /**
     * Defined in the "DiscoveryDriver" interface.
     * @param storageProvider
     * @param storageSystems
     * @return
     */
    @Override
    public DriverTask discoverStorageProvider(StorageProvider storageProvider, List<StorageSystem> storageSystems) {
        return this.execute("discoverStorageProvider", storageProvider, storageSystems);
    }
}
