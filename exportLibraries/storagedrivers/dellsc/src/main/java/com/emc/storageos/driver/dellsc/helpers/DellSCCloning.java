/*
 * Copyright 2016 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.emc.storageos.driver.dellsc.helpers;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.VolumeClone;
import com.emc.storageos.storagedriver.model.VolumeClone.ReplicationState;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * Helper for cloning operations.
 */
public class DellSCCloning {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCCloning.class);

    private DellSCPersistence persistence;

    /**
     * Initialize the instance.
     * 
     * @param persistence The persistence interface.
     */
    public DellSCCloning(DellSCPersistence persistence) {
        this.persistence = persistence;
    }

    /**
     * Create a clone of a volume.
     *
     * @param clones The clones to create.
     * @param storageCapabilities The requested capabilities for the clones.
     * @return The clone task.
     */
    public DriverTask createVolumeClone(List<VolumeClone> clones, StorageCapabilities storageCapabilities) {
        LOG.info("Creating volume clone");
        DellSCDriverTask task = new DellSCDriverTask("createVolumeClone");
        StringBuilder errBuffer = new StringBuilder();
        int createCount = 0;

        for (VolumeClone clone : clones) {
            try (StorageCenterAPI api = persistence.getSavedConnection(clone.getStorageSystemId())) {
                // Create temporary replay to create the clone from
                ScReplay replay = api.createReplay(clone.getParentId(), 5);

                // Now create a new volume from the snapshot
                ScVolume scVol = api.createViewVolume(clone.getDisplayName(), replay.instanceId);
                clone.setProvisionedCapacity(SizeUtil.sizeStrToBytes(scVol.configuredSize));
                clone.setAllocatedCapacity(0L); // New volumes don't allocate any space

                clone.setWwn(scVol.deviceId);
                clone.setNativeId(scVol.instanceId);
                clone.setDeviceLabel(scVol.name);
                clone.setAccessStatus(AccessStatus.READ_WRITE);
                clone.setReplicationState(ReplicationState.SYNCHRONIZED);

                createCount++;
            } catch (DellSCDriverException | StorageCenterAPIException dex) {
                String error = String.format(
                        "Error creating clone of volume %s: %s", clone.getParentId(), dex);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (createCount == clones.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (createCount == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Delete volume clone.
     *
     * @param clone The clone to delete.
     * @return The delete task.
     */
    public DriverTask deleteVolumeClone(VolumeClone clone) {
        LOG.info("Deleting volume clone {}", clone);
        DellSCDriverTask task = new DellSCDriverTask("deleteVolumeClone");

        try (StorageCenterAPI api = persistence.getSavedConnection(clone.getStorageSystemId())) {
            api.deleteVolume(clone.getNativeId());
            task.setStatus(TaskStatus.READY);
        } catch (StorageCenterAPIException | DellSCDriverException dex) {
            String error = String.format(
                    "Error deleting volume clone %s: %s", clone.getNativeId(), dex);
            LOG.error(error);
            task.setFailed(error);
        }
        return task;
    }

    /**
     * Detach volume clones.
     *
     * @param clones The clones to detach.
     * @return The detach task.
     */
    public DriverTask detachVolumeClone(List<VolumeClone> clones) {
        LOG.info("Detaching volume clone");

        // Clones are not connected to the original volume, so already "detached".
        DriverTask task = new DellSCDriverTask("detachVolumeClone");
        task.setStatus(TaskStatus.READY);
        return task;
    }

    /**
     * Restore from the volume clone.
     *
     * @param clones The clones to restore.
     * @return The restore task.
     */
    public DriverTask restoreFromClone(List<VolumeClone> clones) {
        LOG.info("Restoring volume clone");
        DriverTask task = new DellSCDriverTask("restoreVolumeClone");
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Restore from clone not currently supported.");
        return task;
    }
}
