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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.emc.storageos.driver.dellsc.DellSCDriverException;
import com.emc.storageos.driver.dellsc.DellSCDriverTask;
import com.emc.storageos.driver.dellsc.DellSCUtil;
import com.emc.storageos.driver.dellsc.scapi.SizeUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplayProfile;
import com.emc.storageos.driver.dellsc.scapi.objects.ScVolume;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.StorageObject.AccessStatus;
import com.emc.storageos.storagedriver.model.StorageVolume;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * Helper class for driver provisioning operations.
 */
public class DellSCProvisioning {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCProvisioning.class);

    private DellSCPersistence persistence;

    /**
     * Initialize the instance.
     * 
     * @param persistence The persistence interface.
     */
    public DellSCProvisioning(DellSCPersistence persistence) {
        this.persistence = persistence;
    }

    /**
     * Create storage volumes with a given set of capabilities.
     * Before completion of the request, set all required data for provisioned
     * volumes in "volumes" parameter.
     *
     * @param volumes Input/output argument for volumes.
     * @param storageCapabilities Input argument for capabilities. Defines
     *            storage capabilities of volumes to create.
     * @return The volume creation task.
     */
    public DriverTask createVolumes(List<StorageVolume> volumes, StorageCapabilities storageCapabilities) {
        DriverTask task = new DellSCDriverTask("createVolume");

        Map<String, List<ScReplayProfile>> consistencyGroups = new HashMap<>();
        StringBuilder errBuffer = new StringBuilder();
        int volumesCreated = 0;
        for (StorageVolume volume : volumes) {
            LOG.debug("Creating volume {} on system {}", volume.getDisplayName(), volume.getStorageSystemId());
            String ssn = volume.getStorageSystemId();
            try (StorageCenterAPI api = persistence.getSavedConnection(ssn)) {
                // See if we need to add to a consistency group
                String cgID = new DellSCUtil().findCG(api, ssn, volume.getConsistencyGroup(), consistencyGroups);

                ScVolume scVol = api.createVolume(
                        ssn,
                        volume.getDisplayName(),
                        volume.getStoragePoolId(),
                        SizeUtil.byteToGig(volume.getRequestedCapacity()),
                        cgID);

                volume.setProvisionedCapacity(SizeUtil.sizeStrToBytes(scVol.configuredSize));
                volume.setAllocatedCapacity(0L); // New volumes don't allocate any space
                volume.setWwn(scVol.deviceId);
                volume.setNativeId(scVol.instanceId);
                volume.setDeviceLabel(scVol.name);
                volume.setAccessStatus(AccessStatus.READ_WRITE);

                volumesCreated++;
                LOG.info("Created volume '{}'", scVol.name);
            } catch (StorageCenterAPIException | DellSCDriverException dex) {
                String error = String.format("Error creating volume %s: %s", volume.getDisplayName(), dex);
                LOG.error(error);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (volumesCreated == volumes.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (volumesCreated == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Expand volume to a new size.
     * Before completion of the request, set all required data for expanded
     * volume in "volume" parameter.
     *
     * @param storageVolume Volume to expand. Type: Input/Output argument.
     * @param newCapacity Requested capacity. Type: input argument.
     * @return The volume expansion task.
     */
    public DriverTask expandVolume(StorageVolume storageVolume, long newCapacity) {
        DriverTask task = new DellSCDriverTask("expandVolume");
        try (StorageCenterAPI api = persistence.getSavedConnection(storageVolume.getStorageSystemId())) {
            ScVolume scVol = api.expandVolume(storageVolume.getNativeId(), SizeUtil.byteToGig(newCapacity));
            storageVolume.setProvisionedCapacity(SizeUtil.sizeStrToBytes(scVol.configuredSize));

            task.setStatus(TaskStatus.READY);
            LOG.info("Expanded volume '{}'", scVol.name);
        } catch (DellSCDriverException | StorageCenterAPIException dex) {
            String error = String.format("Error expanding volume %s: %s",
                    storageVolume.getDisplayName(), dex);
            LOG.error(error);
            task.setMessage(error);
            task.setStatus(TaskStatus.FAILED);
        }

        return task;
    }

    /**
     * Delete volume.
     *
     * @param volume The volume to delete.
     * @return The volume deletion task.
     */
    public DriverTask deleteVolume(StorageVolume volume) {
        DellSCDriverTask task = new DellSCDriverTask("deleteVolume");

        try (StorageCenterAPI api = persistence.getSavedConnection(volume.getStorageSystemId())) {
            api.deleteVolume(volume.getNativeId());

            task.setStatus(TaskStatus.READY);
            LOG.info("Deleted volume '{}'", volume.getNativeId());
        } catch (DellSCDriverException | StorageCenterAPIException dex) {
            String error = String.format("Error deleting volume %s", volume.getNativeId(), dex);
            LOG.error(error);
            task.setFailed(error);
        }

        return task;
    }
}
