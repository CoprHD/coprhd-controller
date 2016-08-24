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
import com.emc.storageos.driver.dellsc.DellSCUtil;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPI;
import com.emc.storageos.driver.dellsc.scapi.StorageCenterAPIException;
import com.emc.storageos.driver.dellsc.scapi.objects.ScReplay;
import com.emc.storageos.storagedriver.DriverTask;
import com.emc.storageos.storagedriver.DriverTask.TaskStatus;
import com.emc.storageos.storagedriver.model.VolumeSnapshot;
import com.emc.storageos.storagedriver.storagecapabilities.StorageCapabilities;

/**
 * Snapshot handling.
 */
public class DellSCSnapshots {

    private static final Logger LOG = LoggerFactory.getLogger(DellSCSnapshots.class);

    private DellSCConnectionManager connectionManager;
    private DellSCUtil util;

    /**
     * Initialize the instance.
     */
    public DellSCSnapshots() {
        this.connectionManager = DellSCConnectionManager.getInstance();
        this.util = DellSCUtil.getInstance();
    }

    /**
     * Create volume snapshots.
     *
     * @param snapshots The list of snapshots to create.
     * @param storageCapabilities The requested capabilities of the snapshots.
     * @return The snapshot creation task.
     */
    public DriverTask createVolumeSnapshot(List<VolumeSnapshot> snapshots, StorageCapabilities storageCapabilities) {
        DellSCDriverTask task = new DellSCDriverTask("createVolumeSnapshot");
        StringBuilder errBuffer = new StringBuilder();
        int createCount = 0;

        for (VolumeSnapshot snapshot : snapshots) {
            try {
                StorageCenterAPI api = connectionManager.getConnection(snapshot.getStorageSystemId());

                // Make sure we can create a replay.
                // Automated tests have an artificial workflow where they create a volume
                // and try to create a snapshot without ever having data written to it. The
                // SC array will not activate a volume until it is mapped, so if we try to
                // create a snapshot right away it will fail. As a workaround, since we know
                // this should only ever happen in a test scenario, we temporarily map/unmap
                // it to get it to be activated.
                api.checkAndInitVolume(snapshot.getParentId());

                ScReplay replay = api.createReplay(snapshot.getParentId());
                util.getVolumeSnapshotFromReplay(replay, snapshot);
                createCount++;
            } catch (DellSCDriverException | StorageCenterAPIException dex) {
                String error = String.format(
                        "Error creating snapshot of volume %s: %s", snapshot.getParentId(), dex);
                errBuffer.append(String.format("%s%n", error));
            }
        }

        task.setMessage(errBuffer.toString());

        if (createCount == snapshots.size()) {
            task.setStatus(TaskStatus.READY);
        } else if (createCount == 0) {
            task.setStatus(TaskStatus.FAILED);
        } else {
            task.setStatus(TaskStatus.PARTIALLY_FAILED);
        }

        return task;
    }

    /**
     * Restore volumes to a snapshot point in time.
     * NOTE: not currently supported.
     * 
     * @param snapshots The snapshots to restore to.
     * @return The driver task.
     */
    public DriverTask restoreSnapshot(List<VolumeSnapshot> snapshots) {
        DriverTask task = new DellSCDriverTask("restoreVolumeSnapshot");
        // The API to revert a volume to a specific snapshot is currently
        // a private call. We can either support this by:
        // 1) Creating a volume from the snapshot, perform a copy back to the original, delete volume
        // 2) Wait until the snapshot revert call can be made public
        // Going with option 2 for now.
        task.setStatus(TaskStatus.FAILED);
        task.setMessage("Snapshot restore is not supported at this time.");
        LOG.warn("Snapshot restore is not supported at this time.");
        return null;
    }

    /**
     * Delete a snapshot.
     *
     * @param snapshot The snapshots to delete.
     * @return The delete task.
     */
    public DriverTask deleteVolumeSnapshot(VolumeSnapshot snapshot) {
        DellSCDriverTask task = new DellSCDriverTask("deleteVolumeSnapshot");

        try {
            StorageCenterAPI api = connectionManager.getConnection(snapshot.getStorageSystemId());
            api.expireReplay(snapshot.getNativeId());
            task.setStatus(TaskStatus.READY);
        } catch (StorageCenterAPIException | DellSCDriverException dex) {
            String error = String.format(
                    "Error deleting snapshot %s: %s", snapshot.getNativeId(), dex);
            LOG.error(error);
            task.setFailed(error);
        }
        return task;
    }
}
