/*
 * Copyright (c) 2017 EMC Corporation
 * All Rights Reserved
 */
package com.emc.storageos.driver.vmax3;

import java.util.List;
import java.util.UUID;

import com.emc.storageos.storagedriver.DefaultDriverTask;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.LockManager;
import com.emc.storageos.storagedriver.Registry;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * All the code is just fake code.
 *
 */
public class ProvisioningHelper extends AbstractHelper {

    /**
     * @param driverRegistry
     * @param arrayId
     */
    public ProvisioningHelper(Registry driverRegistry, LockManager lockManager, String arrayId) {
        super(driverRegistry, lockManager, arrayId);
    }

    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities capabilities) {
        String driverName = this.getClass().getSimpleName();
        String taskId = String.format("%s+%s+%s", driverName, "discover-storage-provider", UUID.randomUUID().toString());
        DriverTask task = new DefaultDriverTask(taskId);
        String sgId = volumes.get(0).getConsistencyGroup();

        this.managerFactory.genStorageGroupManager().createEmptySg(null);
        this.managerFactory.genStorageGroupManager().createNewVolInSg(null, null);
        return task;
    }
}
